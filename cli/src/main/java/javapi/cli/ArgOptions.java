package javapi.cli;

import java.util.ArrayList;
import java.util.List;

/** Minimal {@code --opt value} / {@code --opt value --opt value} argument parsing. */
final class ArgOptions {

    private final List<String> args;

    ArgOptions(List<String> args) {
        this.args = args;
    }

    String positional() {
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.startsWith("--")) {
                i++;
            } else {
                return a;
            }
        }
        return null;
    }

    String opt(String name) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(name) && i + 1 < args.size()) {
                return args.get(i + 1);
            }
        }
        return null;
    }

    int optInt(String name, int defaultValue) {
        String value = opt(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CliException(name + " expects an integer, got: " + value);
        }
    }

    List<String> collect(String name) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals(name) && i + 1 < args.size()) {
                out.add(args.get(i + 1));
            }
        }
        return out;
    }
}
