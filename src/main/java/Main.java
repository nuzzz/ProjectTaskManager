import java.util.logging.Logger;

public class Main {

    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        ProjectTaskManager ptm = new ProjectTaskManager();
        ptm.run();
        LOGGER.info("ProjectTaskManager.run() executing... ");
    }


}
