package terry.bluesync.server.util;

public class SessionInfo {
    private static SessionInfo session;
    private String ticket;

    private SessionInfo(){}

    public static SessionInfo getInstance() {
        if (session == null) {
            session = new SessionInfo();
        }
        return session;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public String getTicket() {
        return ticket;
    }
}
