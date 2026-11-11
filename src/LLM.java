public interface LLM{
    // leve LlmException si le LLM ne repond pas : une erreur ne doit jamais passer pour une reponse
    public String ask(String systemPrompt, String message);

}