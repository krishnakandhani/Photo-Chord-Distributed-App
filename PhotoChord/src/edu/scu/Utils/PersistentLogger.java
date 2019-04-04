package edu.scu.Utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class PersistentLogger {
    static PersistentLogger _logger;

    static {
        try {
            _logger = new PersistentLogger();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    FileWriter _log = new FileWriter("log.txt");
    int random = new Random().nextInt(1000);

    private PersistentLogger() throws IOException {
        Logger.log("Logger started: log" + random + ".txt");
    }

    public static PersistentLogger getInstance() {
        return _logger;
    }

    public void logE(String s) {
        try {
            _log.write("Error: " + s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logD(String s)  {
        try {
            _log.write("Debug: " + s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void finish() throws IOException {
        _log.close();
    }
}
