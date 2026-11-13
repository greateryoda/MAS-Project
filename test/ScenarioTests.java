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

    // ---------- piste 2 : selection du contexte ----------
    static void contextSelection(List<DemoEmail> demo) throws Exception {
        System.out.println("13. Context selection");
        Email exam = demo.get(0).toEmail();
        Setup s = setup(false);
        Agent student = s.assistant().getRegistry().get("STUDENT");
        ContextSelection ctx = student.selectContext(exam);
        String chosen = ctx.base().stream().map(Belief::getContent).toList().toString();
        check("lexical is the default strategy", ctx.strategy().equals("lexical"), ctx.strategy());
        check("the exam belief is selected", chosen.contains("Final exam"), chosen);
        check("unrelated beliefs are left out", !chosen.contains("TP1 Flocking") && ctx.base().size() < ctx.baseAvailable(),
              ctx.summary());
        check("Terms: accents, case and stemming", Terms.of("Réunion EXAMEN examens TP2 the").equals(List.of("reun", "exam", "exam", "tp2")),
              Terms.of("Réunion EXAMEN examens TP2 the").toString());

        List<Belief> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(new Belief("exam fact number " + i, 1, Belief.Source.BASE, Belief.NO_THREAD, "d"));
        check("maxBeliefs is respected", new LexicalContextSelector().select(exam, many, 5).size() == 5, "");
        check("AllContextSelector keeps everything", new AllContextSelector().select(exam, many, 5).size() == 20, "");
        List<Belief> recalled = student.recall(Belief.NO_THREAD);
        check("recall(NO_THREAD) returns each base belief once", recalled.size() == ctx.baseAvailable(),
              recalled.size() + " vs " + ctx.baseAvailable());

        // embeddings simules : vecteur [1,0] si le texte parle d'examen, [0,1] sinon
        Embedder fake = texts -> texts.stream()
                .map(t -> t.toLowerCase().contains("exam") ? new double[] {1, 0} : new double[] {0, 1}).toList();
        List<Belief> base = student.recall(Belief.NO_THREAD).stream().filter(b -> b.getSource() == Belief.Source.BASE).toList();
        List<Belief> picked = new EmbeddingContextSelector(fake, new LexicalContextSelector()).select(exam, base, 1);
        check("embedding selection ranks by cosine similarity", picked.size() == 1 && picked.get(0).getContent().toLowerCase().contains("exam"),
              picked.toString());
        Embedder down = texts -> { throw new LlmException("embedding server down", null); };
        EmbeddingContextSelector degraded = new EmbeddingContextSelector(down, new LexicalContextSelector());
        List<Belief> fallback = degraded.select(exam, base, 3);
        check("embedding failure falls back to lexical", !fallback.isEmpty() && degraded.name().contains("lexical"), degraded.name());
        int[] calls = {0};
        CachingEmbedder cache = new CachingEmbedder(texts -> { calls[0] += texts.size(); return fake.embed(texts); });
        cache.embed(List.of("a", "b"));
        cache.embed(List.of("a", "b", "c"));
        check("CachingEmbedder embeds each text once", calls[0] == 3, "calls=" + calls[0]);

        boolean rejected = false;
        try { ContextSelectorFactory.defaults().create("magic"); } catch (IllegalArgumentException e) { rejected = true; }
        check("unknown strategy is rejected", rejected, "");
        check("forcing overrides the agent's strategy",
              ContextSelectorFactory.defaults().forcing("all").create("lexical").name().equals("all"), "");

        // strategie par agent dans agent.json
        Path json = Path.of("agents/student/agent.json");
        String original = Files.readString(json);
        Files.writeString(json, original.replaceFirst("\\{", "{\"context\": {\"strategy\": \"all\", \"maxThreadMessages\": 1},"));
        student.reload();
        ctx = student.selectContext(exam);
        check("per-agent strategy read from agent.json", ctx.strategy().equals("all") && ctx.base().size() == ctx.baseAvailable(), ctx.summary());
        Files.writeString(json, original);
        student.reload();

        // le fil recent est toujours transmis, borne par maxThreadMessages (fil 1001 memorise au test 4)
        ctx = s.assistant().getRegistry().get("STUDENT").selectContext(demo.get(5).toEmail());
        check("recent thread messages always sent", ctx.thread().size() >= 2 && ctx.threadAvailable() >= 2, ctx.summary());
        s.validator().answers.add(Feedback.discard());
        s.events().events.clear();
        s.assistant().process(exam);
        check("draft prompt only contains the selected context", !s.llm().messages.get(s.llm().messages.size() - 1).contains("TP1 Flocking"), "");
    }

    // ---------- piste 8 : journal des decisions ----------
    static void decisionLog(List<DemoEmail> demo) throws Exception {
        System.out.println("14. Decision log");
        Path file = Path.of("logs/test-decisions.jsonl");
        Files.deleteIfExists(file);
        Setup s = setup(true);
        s.assistant().addListener(new DecisionLogger(file.toString(), "scripted", false));
        s.validator().answers.add(Feedback.reject("Too long"));
        s.validator().answers.add(Feedback.accept());
        s.assistant().process(new Email("lea.martin@etu.univ.fr", "TP2 deadline", "When is the TP2 deadline? SECRET-BODY",
                                        "01/12/2026", 2001));
        s.validator().answers.add(Feedback.discard());
        s.assistant().process(demo.get(4).toEmail());
        s.llm().down = true;
        try { s.assistant().process(demo.get(2).toEmail()); } catch (LlmException e) { /* attendu */ }

        List<com.fasterxml.jackson.databind.JsonNode> log = DecisionLogReport.read(file);
        check("one line per processed email (including failures)", log.size() == 3, "lines=" + log.size());
        if (log.size() == 3) {
            var first = log.get(0);
            check("routing explained (agent, signals, scores, explanation)",
                  first.path("routing").path("role").asText().equals("STUDENT")
                  && first.path("routing").path("signals").size() > 0
                  && first.path("routing").path("explanation").asText().contains("without the LLM"), first.path("routing").toString());
            check("context selection logged", first.path("context").path("strategy").asText().equals("lexical")
                  && first.path("context").path("beliefsAvailable").asInt() > 0, first.path("context").toString());
            check("each draft logged with critique and feedback",
                  first.path("attempts").size() == 2
                  && first.path("attempts").path(0).path("critique").path("score").asInt() == 6
                  && first.path("attempts").path(0).path("feedback").asText().equals("REJECT")
                  && first.path("attempts").path(0).path("reason").asText().equals("Too long")
                  && first.path("attempts").path(1).path("feedback").asText().equals("ACCEPT"), first.path("attempts").toString());
            check("outcome and draft count logged", first.path("outcome").asText().equals("ACCEPTED")
                  && first.path("draftCount").asInt() == 2, first.toString());
            check("email body and drafts not written by default", !Files.readString(file).contains("SECRET-BODY")
                  && !first.path("attempts").path(0).has("draft"), "");
            check("arbiter decision logged", log.get(1).path("routing").path("arbiterConsulted").asBoolean()
                  && log.get(1).path("outcome").asText().equals("DISCARDED"), log.get(1).toString());
            check("failure logged with its stage", log.get(2).path("outcome").asText().equals("ERROR")
                  && log.get(2).path("failedStage").asText().equals("GENERATION"), log.get(2).toString());
        }
        String report = DecisionLogReport.markdown(log, file.toString());
        check("log report: per agent stats, calibration, reasons",
              report.contains("StudentAgent") && report.contains("| REJECT | 1 | 6.00 |") && report.contains("Too long"), report);
    }

    // ---------- piste 1 : experiences ----------
    static void evaluation() throws Exception {
        System.out.println("15. Evaluation harness");
        List<LabelledEmail> dataset = LabelledEmail.load("eval/dataset.json");
        check("dataset loaded with labels", dataset.size() >= 25 && dataset.stream().anyMatch(e -> e.acceptable().size() > 1),
              "size=" + dataset.size());

        ScriptedLlm llm = new ScriptedLlm();
        AgentRegistry registry = new AgentRegistry(new File("agents"), new AgentFactory(llm));
        TemporaryContactBook contacts = new TemporaryContactBook("agents/contacts.json");
        RoutingEvaluation routing = new RoutingEvaluation(registry, contacts, llm);
        List<RoutingEvaluation.ConfigResult> runs = new ArrayList<>();
        for (String c : RoutingEvaluation.CONFIGS.keySet()) runs.add(routing.run(c, dataset));
        RoutingEvaluation.ConfigResult full = runs.get(0), rules = runs.get(1), llmOnly = runs.get(2);
        check("every configuration routes every email", runs.stream().allMatch(r -> r.results().size() == dataset.size()
              && r.results().stream().noneMatch(x -> x.predicted().equals("ERROR"))), "");
        check("rules-only never calls the LLM", rules.llm().calls() == 0, "calls=" + rules.llm().calls());
        check("llm-only calls the LLM for every email", llmOnly.llm().calls() == dataset.size() && llmOnly.arbiterRate() == 1.0,
              "calls=" + llmOnly.llm().calls());
        check("full routing beats the (scripted) LLM alone", full.lenientAccuracy() > llmOnly.lenientAccuracy(),
              full.lenientAccuracy() + " vs " + llmOnly.lenientAccuracy());
        check("lenient accuracy >= strict accuracy", full.lenientAccuracy() >= full.strictAccuracy(), "");
        String md = RoutingEvaluation.markdown(runs, "scripted", "eval/dataset.json");
        check("routing report has summary, confusion matrix and errors",
              md.contains("| full |") && md.contains("Confusion matrix") && md.contains("Precision") && md.contains("Errors ("), "");
        check("routing CSV has one row per email and config",
              RoutingEvaluation.csv(runs).split("\n").length == 1 + dataset.size() * runs.size(), "");

        MeteredLLM meter = new MeteredLLM(llm);
        meter.ask("sys", "hello");
        check("MeteredLLM counts calls and prompt size", meter.snapshot().calls() == 1 && meter.snapshot().promptChars() == 8, "");

        List<LabelledEmail> replies = dataset.stream().filter(LabelledEmail::reply).limit(3).toList();
        CriticAgent judge = new CriticAgent(llm, "critic/attitude_critic.txt", true);
        ReplyEvaluation evaluation = new ReplyEvaluation(llm, ContextSelectorFactory.defaults(), judge, registry, contacts);
        List<ReplyEvaluation.ConfigResult> rr = new ArrayList<>();
        for (String c : ReplyEvaluation.CONFIGS.keySet()) rr.add(evaluation.run(c, replies));
        ReplyEvaluation.ConfigResult multi = rr.get(0), all = rr.get(1), single = rr.get(3);
        check("every reply configuration produces judged drafts",
              rr.stream().allMatch(r -> r.errors() == 0 && r.unjudged() == 0 && r.meanScore() == 6.0), "");
        check("context selection shrinks the prompt", multi.meanPrompt() < all.meanPrompt(),
              multi.meanPrompt() + " vs " + all.meanPrompt());
        check("single agent sends the largest prompt", single.meanPrompt() > all.meanPrompt(),
              single.meanPrompt() + " vs " + all.meanPrompt());
        check("reply report and JSON produced", ReplyEvaluation.markdown(rr, "scripted", "scripted", "d").contains("| single-agent |")
              && ReplyEvaluation.json(rr).contains("\"draft\""), "");
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

        contextSelection(demo);
        decisionLog(demo);
        evaluation();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
