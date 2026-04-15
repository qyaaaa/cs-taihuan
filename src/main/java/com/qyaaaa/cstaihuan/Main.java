package com.qyaaaa.cstaihuan;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = new CliApplication().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}

