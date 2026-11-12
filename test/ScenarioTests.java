import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class ScenarioTests {
    private static int passed = 0;
    private static int failed = 0;

    static class ScriptedLlm implements LLM {
        final List<String> systemPrompts = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        int classifierCalls = 0;
        int criticCalls = 0;
        int drafts = 0;
        boolean down = false;

        public String ask(String systemPrompt, String message) {
            if (down) throw new LlmException("LLM server stopped", null);
            systemPrompts.add(systemPrompt);
            messages.add(message);
            if (systemPrompt.startsWith("You classify emails")) {
                classifierCalls++;
                String role = message.contains("- ADMINISTRATION:") && message.contains("registration form")
                        ? "ADMINISTRATION" : "GENERIC";
                return "Sure: {\"role\": \"" + role + "\", \"confidence\": 0.82}";
            }
            if (systemPrompt.contains("You are CriticAgent")) {
                criticCalls++;
                return "{\"score\": 6, \"approved\": true, \"issues\": [\"Recall the exam room\", \"Shorter greeting\"]}";
            }
            drafts++;
            return "Draft " + drafts + " (" + systemPrompt.length() + " chars of system prompt)";
        }
    }

    static class ScriptedValidator implements Validator {
        final Deque<Feedback> answers = new ArrayDeque<>();
        final List<Proposal> proposals = new ArrayList<>();
        String manualReply = null;

        public Feedback review(Proposal p) {
            proposals.add(p);
            return answers.isEmpty() ? Feedback.accept() : answers.poll();
        }

        public String writeReply(Email email) {
            return manualReply;
        }
    }

    static class RecordingListener implements WorkflowListener {
        final List<String> events = new ArrayList<>();
        String selected;
        boolean arbiter;

        public void stageChanged(WorkflowStage stage, StageStatus status, String detail) {
            events.add(stage + ":" + status);
        }

        public void agentSelected(Agent agent, RoutingDecision decision, Map<String, Double> scores,
                                  List<String> signals, boolean arbiterConsulted) {
            selected = agent.getRole().toUpperCase();
            arbiter = arbiterConsulted;
        }
    }

    record Setup(EmailAssistant assistant, ScriptedLlm llm, ScriptedValidator validator, RecordingListener events) {}

    static Setup setup(boolean critic) throws Exception {
        ScriptedLlm llm = new ScriptedLlm();
        ScriptedValidator validator = new ScriptedValidator();
        EmailAssistant assistant = new EmailAssistant(llm, validator, "critic/attitude_critic.txt", critic);
        RecordingListener events = new RecordingListener();
        assistant.addListener(events);
        return new Setup(assistant, llm, validator, events);
    }

    static void check(String name, boolean condition, String detail) {
        if (condition) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name + "  -> " + detail); }
    }

    static void clearMemory() throws Exception {
        File[] dirs = new File("agents").listFiles(File::isDirectory);
        for (File dir : dirs) {
            File[] files = dir.listFiles((d, n) -> n.startsWith("learnt_beliefs_"));
            for (File f : files) Files.delete(f.toPath());
        }
    }

    public static void main(String[] args) throws Exception {
        clearMemory();
        List<DemoEmail> demo = DemoEmail.load("demo/emails.json");

        System.out.println("1. Agent loading");
        Setup s = setup(true);
        List<String> roles = s.assistant().getRegistry().list().stream().map(a -> a.getRole().toUpperCase()).toList();
        check("five specialized agents loaded", roles.size() == 5, roles.toString());
        check("AdministrationAgent loaded", roles.contains("ADMINISTRATION"), roles.toString());
        check("ResearchProjectAgent loaded", roles.contains("RESEARCHPROJECT"), roles.toString());

        System.out.println("2. Routing of the demonstration emails");
        String[] expected = {"STUDENT", "TEACHER", "ADMINISTRATION", "RESEARCHPROJECT", "ADMINISTRATION"};
        for (int i = 0; i < 5; i++) {
            s = setup(false);
            s.validator().answers.add(Feedback.discard());
            s.assistant().process(demo.get(i).toEmail());
            check(demo.get(i).label() + " -> " + expected[i], expected[i].equals(s.events().selected),
                  "got " + s.events().selected);
            if (i == 4) check("ambiguous email consults the LLM arbiter", s.events().arbiter && s.llm().classifierCalls == 1,
                              "arbiter=" + s.events().arbiter);
            else check("no arbiter needed for " + expected[i], !s.events().arbiter, "arbiter consulted");
        }

        System.out.println("3. Unknown sender with no signal");
        s = setup(false);
        s.validator().answers.add(Feedback.discard());
        s.assistant().process(new Email("someone@example.com", "Hello", "Are you available?", "01/12/2026", 1999));
        check("weak signals -> arbiter among all agents -> GENERIC", "GENERIC".equals(s.events().selected)
              && s.events().arbiter, "got " + s.events().selected);

        System.out.println("4. Accept: reply saved, then the thread is recognised");
        s = setup(false);
        Intention it = s.assistant().process(demo.get(0).toEmail());
        String memory = Files.readString(Path.of("agents/student/learnt_beliefs_student.txt"));
        check("intention achieved", it.getStatus() == Intention.Status.ACHIEVED, it.getStatus().toString());
        check("memory contains the email and the accepted reply",
              memory.contains("1001|EMAIL") && memory.contains("1001|REPLY") && memory.contains("Draft 1"), memory);
        s = setup(false);
        s.assistant().process(demo.get(5).toEmail());
        check("follow-up routed to StudentAgent", "STUDENT".equals(s.events().selected), s.events().selected);
        String prompt = s.llm().messages.get(s.llm().messages.size() - 1);
        check("thread history sent to the LLM", prompt.contains("Reply: Draft 1"), prompt);

        System.out.println("5. Modify: the edited reply is memorised");
        s = setup(false);
        s.validator().answers.add(Feedback.modify("Edited reply about the jury meeting"));
        it = s.assistant().process(demo.get(1).toEmail());
        memory = Files.readString(Path.of("agents/teacher/learnt_beliefs_teacher.txt"));
        check("final reply is the edited text", "Edited reply about the jury meeting".equals(it.getFinalReply()), it.getFinalReply());
        check("edited text memorised", memory.contains("Edited reply about the jury meeting"), memory);

        System.out.println("6. Reject with remarks then accept");
        s = setup(false);
        s.validator().answers.add(Feedback.reject("Mention deliverable D2.1 deadline"));
        s.validator().answers.add(Feedback.accept());
        it = s.assistant().process(demo.get(3).toEmail());
        String second = s.llm().messages.get(s.llm().messages.size() - 1);
        check("two drafts generated", it.getAttempts() == 2, "attempts=" + it.getAttempts());
        check("remark sent to the agent", second.contains("Mention deliverable D2.1 deadline"), second);
        check("second draft accepted", it.getStatus() == Intention.Status.ACHIEVED && "Draft 2".equals(it.getFinalReply().substring(0, 7)), it.getFinalReply());

        System.out.println("7. Discard: nothing is memorised");
        s = setup(false);
        s.validator().answers.add(Feedback.discard());
        it = s.assistant().process(new Email("rh@univ.fr", "Form to sign", "Please sign the form.", "01/12/2026", 1777));
        String adminMemory = s.assistant().getRegistry().readMemory("ADMINISTRATION");
        check("intention discarded", it.getStatus() == Intention.Status.DISCARDED, it.getStatus().toString());
        check("discarded thread not in memory", !adminMemory.contains("1777|"), adminMemory);

        System.out.println("8. Too many rejections: intention dropped, manual reply possible");
        s = setup(false);
        for (int i = 0; i < 3; i++) s.validator().answers.add(Feedback.reject(""));
        s.validator().manualReply = null;
        it = s.assistant().process(new Email("rh@univ.fr", "Certificate", "Can you send the certificate?", "01/12/2026", 1778));
        check("dropped after maxRetries + 1 drafts", it.getStatus() == Intention.Status.DROPPED && it.getAttempts() == 3,
              it.getStatus() + " attempts=" + it.getAttempts());
        s = setup(false);
        for (int i = 0; i < 3; i++) s.validator().answers.add(Feedback.reject("no"));
        s.validator().manualReply = "I will send it tomorrow.";
        it = s.assistant().process(new Email("rh@univ.fr", "Certificate", "Can you send the certificate?", "01/12/2026", 1779));
        check("manual reply memorised", it.getStatus() == Intention.Status.ACHIEVED
              && s.assistant().getRegistry().readMemory("ADMINISTRATION").contains("I will send it tomorrow."), it.getStatus().toString());

        System.out.println("9. CriticAgent");
        s = setup(true);
        s.assistant().process(new Email("lea.martin@etu.univ.fr", "TP2 deadline", "When is the TP2 deadline?", "01/12/2026", 1800));
        Critique c = s.validator().proposals.get(0).getCritique();
        check("critique attached to the proposal", c != null && c.isAvailable() && c.getScore() == 6, String.valueOf(c));
        check("score below 7 is never approved", c != null && !c.isApproved(), String.valueOf(c == null ? null : c.summary()));
        check("critic received the agent rules", s.llm().messages.stream().anyMatch(m -> m.contains("RULES GIVEN TO THE AGENT") && m.contains("Commit to extensions")), "");
        s.assistant().getCritic().setEnabled(false);
        s.events().events.clear();
        s.assistant().process(new Email("lea.martin@etu.univ.fr", "TP3 deadline", "When is the TP3 deadline?", "01/12/2026", 1801));
        check("disabled critic is skipped", s.events().events.contains("CRITIC:SKIPPED"), s.events().events.toString());

        System.out.println("10. Workflow events");
        List<String> ev = s.events().events;
        String[] order = {"RECEIVED:COMPLETED", "ROUTING:PROCESSING", "SIGNALS:COMPLETED", "ROUTING:COMPLETED",
                "SELECTION:COMPLETED", "TRANSMISSION:COMPLETED", "CONTEXT:COMPLETED", "GENERATION:PROCESSING",
                "GENERATION:COMPLETED", "CRITIC:SKIPPED", "PROPOSAL:COMPLETED", "VALIDATION:PROCESSING", "VALIDATION:COMPLETED"};
        int pos = -1; boolean ordered = true;
        for (String e : order) { int p = ev.indexOf(e); if (p < pos) ordered = false; pos = p; }
        check("all stages emitted in order", ordered && !ev.contains(null), ev.toString());

        System.out.println("11. LLM failure");
        s = setup(false);
        s.llm().down = true;
        boolean thrown = false;
        try { s.assistant().process(demo.get(0).toEmail()); } catch (LlmException e) { thrown = true; }
        check("LlmException propagated", thrown, "");
        check("generation stage marked as failed", s.events().events.contains("GENERATION:FAILED"), s.events().events.toString());

        System.out.println("12. Agent management");
        s = setup(false);
        AgentRegistry reg = s.assistant().getRegistry();
        reg.createAgent("ThesisAgent", "Thesis", "Replies to PhD candidates about their thesis",
                List.of("thesis", "these", "manuscript"), 2, "ROLE: You help with thesis questions.",
                "Defense period: June 2027\n2026-10-01 | Thesis committee meets in May");
        check("new agent registered", reg.get("THESIS") != null, "");
        check("context lines without date are normalised", reg.readFile("agents/thesis/base_beliefs_thesis.txt").startsWith(java.time.LocalDate.now() + " | Defense"), "");
        s.validator().answers.add(Feedback.discard());
        s.assistant().process(new Email("x.y@example.org", "Thesis manuscript", "About my thesis manuscript (ma these)...", "01/12/2026", 1900));
        check("new agent selected by its keywords", "THESIS".equals(s.events().selected), s.events().selected);
        reg.saveSystemPrompt("THESIS", "ROLE: edited prompt");
        check("system prompt change applied to the running agent", reg.get("THESIS").getSystemPrompt().equals("ROLE: edited prompt"), "");
        s.validator().answers.add(Feedback.discard());
        s.assistant().process(new Email("x.y@example.org", "Thesis manuscript", "About my thesis manuscript (ma these)...", "01/12/2026", 1901));
        check("edited system prompt used for generation", s.llm().systemPrompts.contains("ROLE: edited prompt"), "");
        reg.updateProfile("THESIS", "Thesis questions", List.of("dissertation"), 1);
        check("keywords updated live", reg.get("THESIS").getKeywords().equals(List.of("dissertation")), reg.get("THESIS").getKeywords().toString());
        Path extra = Files.createTempFile("guidelines", ".txt");
        Files.writeString(extra, "Manuscripts must be sent 6 weeks before the defense\n");
        String added = reg.importContextFile("THESIS", extra.toFile());
        check("imported file associated as context", reg.configOf("THESIS").getContextFiles().contains(added)
              && reg.get("THESIS").recall(-1).stream().anyMatch(b -> b.getContent().contains("6 weeks")), added);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
