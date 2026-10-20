public class Email {
    private final String senderAddress;
    private final String subject;
    private final String body;
    private final String date;
    private final int threadID;

    public Email(String senderAddress, String subject, String body, String date, int threadID) {
        this.senderAddress = senderAddress;
        this.subject = subject;
        this.body = body;
        this.date = date;
        this.threadID = threadID;
    }

    public String getSenderAddress() { 
        return senderAddress; 
    }

    public String getSubject(){ 
        return subject; 
    }

    public String getBody(){
         return body; 
    }

    public String getDate(){
         return date; 
    }

    public int getThreadID(){
         return threadID; 
    }
}