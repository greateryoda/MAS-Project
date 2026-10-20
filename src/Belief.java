public class Belief{
    public enum Source{
        BASE,
        EMAIL
    }

    private String content;
    private double confidence;
    private int threadID;
    private Source source;
    private String date;
    public static final int NO_THREAD = -1;
    public static final double DEFAULT_CONFIDENCE = 0.2;

    public Belief(String content, double confidence, Source source, int threadID, String date){
        this.content = content;
        this.confidence = confidence;
        this.source = source;
        this.threadID = threadID;
        this.date = date;
    }

    public String getContent() { 
        return content; 
    }

    public double getConfidence(){ 
        return confidence; 
    }

    public int getThreadID(){
         return threadID; 
    }

    public Source getSource(){
         return source; 
    }

    public String getDate(){
         return date; 
    }

    public void setConfidence(double new_confidence){
        this.confidence = new_confidence;
    }


}