package eu.pb4.mrpackserver.util;

import eu.pb4.mrpackserver.Main;
import eu.pb4.mrpackserver.format.ModpackInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class InstallerGui extends JComponent {
    private static final Font FONT_MONOSPACE = new Font("Monospaced", Font.PLAIN, 12);
    private final OutputStream logger;
    private final Charset charset;
    private final PrintStream oldOut;
    private final PrintStream oldErr;
    private final InputStream oldIn;
    private final AppendableInputStream in = new AppendableInputStream();
    private final JFrame frame;
    private JTextArea logBox;
    private boolean closed;

    public static InstallerGui instance = null;

    public InstallerGui(JFrame jFrame) {
        this.frame = jFrame;
        //this.setPreferredSize(new Dimension(954, 480));
        this.setLayout(new BorderLayout());
        this.add(this.createConsole());
        this.charset =  getCharset(System.out);
        this.oldIn = System.in;
        this.oldOut = System.out;
        this.oldErr = System.err;
        this.logger = new OutputStream() {
            @Override
            public void write(byte @NotNull [] bytes, int offset, int length) {
                var text = new String(bytes, offset, length, charset);
                writeToConsole(text);
            }

            @Override
            public void write(int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }

        };


        System.setOut(new PrintStream(new DoubleOutputStream(System.out, logger), false, this.charset));
        System.setErr(new PrintStream(new DoubleOutputStream(System.err, logger), false, this.charset));
        System.setIn(System.console() != null ? new DoubleWaitingInputStream(System.in, this.in) : this.in);

        instance = this;
    }

    private void writeToConsole(String text) {
        SwingUtilities.invokeLater(() -> {
            if (text.contains("Starting minecraft server version")) {
                InstallerGui.this.close();
                return;
            }
            if (this.closed) {
                return;
            }

            logBox.append(text);
        });
    }

    private Charset getCharset(PrintStream err) {
        try {
            return err.charset();
        } catch (Throwable e) {
            try {
                return System.console().charset();
            } catch (Throwable e2) {
                try {
                    return Charset.forName(System.getProperty("stdout.encoding"));
                } catch (Throwable e3) {
                    return Charset.defaultCharset();
                }
            }
        }
    }

    public static boolean setup(@Nullable ModpackInfo info) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception var3) {
        }

        String name;
        if (info != null) {
            name = "mrpack4server | " + info.getDisplayName() + " " + info.getDisplayVersion();
        } else {
            name = "mrpack4server";
        }

        var jFrame = new JFrame(name);
        jFrame.setPreferredSize(new Dimension(800, 480));
        var gui = new InstallerGui(jFrame);
        jFrame.add(gui);
        jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
        jFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
                if (!Main.isLaunched) {
                    System.exit(0);
                }
            }
        });
        return true;
    }

    private Component createConsole() {
        var panel = new JPanel(new BorderLayout());
        this.logBox = new JTextArea();
        this.logBox.setFocusable(false);
        this.logBox.setLineWrap(true);
        var caret = new DefaultCaret();
        caret.setVisible(false);
        //caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        this.logBox.setCaret(caret);

        var scroll = new JScrollPane(logBox, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        logBox.setEditable(false);
        logBox.setFont(FONT_MONOSPACE);
        var input = new JTextField();
        input.addActionListener((event) -> {
            String string = input.getText();
            this.in.append((string + "\n").getBytes(this.charset));
            writeToConsole(string + "\n");
            input.setText("");
        });
        panel.add(scroll, "Center");
        panel.add(input, "South");
        panel.setBorder(new TitledBorder(new EtchedBorder(), "Console"));

        return panel;
    }

    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;

        System.setOut(this.oldOut);
        System.setErr(this.oldErr);
        System.setIn(this.oldIn);
        this.frame.dispose();
        instance = null;
    }

    private static class AppendableInputStream extends InputStream {
        private final ArrayList<byte[]> list = new ArrayList<>();
        private int pos = 0;
        private byte[] curr = null;

        @Override
        public int read() throws IOException {
            if (curr == null) {
                while (true) {
                    synchronized (list) {
                        if (!list.isEmpty()) {
                            break;
                        }
                    }
                    try {
                        Thread.sleep(1);
                    } catch (Throwable e) {
                        return -1;
                    }
                }
                curr = this.list.removeFirst();
            }
            if (curr.length == this.pos) {
                curr = null;
                this.pos = 0;
                return -1;
            }

            var val = Byte.toUnsignedInt(this.curr[this.pos++]);
            return val;
        }

        @Override
        public int available() throws IOException {
            if (curr == null) {
                synchronized (list) {
                    if (list.isEmpty()) {
                        return 0;
                    }
                    return list.getFirst().length;
                }
            }
            return curr.length;
        }

        public void append(byte[] arr) {
            synchronized (this.list) {
                this.list.add(arr);
            }
        }
    }

    private static class DoubleWaitingInputStream extends InputStream {
        private final InputStream out;
        private final InputStream out2;

        public DoubleWaitingInputStream(InputStream out, InputStream out2) {
            this.out = out;
            this.out2 = out2;
        }

        @Override
        public int read() throws IOException {
            var stream = getStream();
            if (stream != null) {
                return stream.read();
            }
            return -1;
        }

        @Override
        public int read(@NotNull byte[] b) throws IOException {
            var stream = getStream();
            if (stream != null) {
                return stream.read(b);
            }
            return 0;
        }

        @Override
        public int read(@NotNull byte[] b, int off, int len) throws IOException {
            var stream = getStream();
            if (stream != null) {
                return stream.read(b, off, len);
            }
            return 0;
        }

        private InputStream getStream() throws IOException {
            while (true) {
                if (this.out.available() > 0) {
                    return this.out;
                }
                if (this.out2.available() > 0) {
                    return this.out2;
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    return null;
                }
            }
        }

        @Override
        public int available() throws IOException {
            return Math.max(this.out.available(), this.out2.available());
        }
    }

    private static class DoubleOutputStream extends OutputStream {
        private final OutputStream out;
        private final OutputStream out2;

        public DoubleOutputStream(OutputStream out, OutputStream out2) {
            this.out = out;
            this.out2 = out2;
        }

        @Override
        public void write(int i) throws IOException {
            this.out.write(i);
            this.out2.write(i);
        }

        @Override
        public void write(@NotNull byte[] b) throws IOException {
            this.out.write(b);
            this.out2.write(b);
        }

        @Override
        public void write(@NotNull byte[] b, int off, int len) throws IOException {
            this.out.write(b, off, len);
            this.out2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            this.out.flush();
            this.out2.flush();
        }

        @Override
        public void close() throws IOException {
            this.out.close();
            this.out2.close();
        }
    }

    /*public void handleForgeFix() {
        Logger.warn("Forge on 1.16.5 and older breaks mrpack4server's initial log screen.");
        Logger.warn("You can safely close it without stopping the server.");
        var currOut = System.out;
        var currErr = System.err;

        try {
            var thread = new Thread(() -> {
                while (currErr == System.err && !this.closed) {
                    Thread.yield();
                }
                if (this.closed) {
                    return;
                }

                this.oldErr = System.err;
                System.setErr(new PrintStream(new DoubleOutputStream(System.err, logger), false, getCharset(System.err)));
            });
            thread.setDaemon(true);
            thread.start();
            thread = new Thread(() -> {
                while (currOut == System.out && !this.closed) {
                    Thread.yield();
                }
                if (this.closed) {
                    return;
                }

                this.oldOut = System.out;
                System.setOut(new PrintStream(new DoubleOutputStream(System.out, logger), false, getCharset(System.out)));
            });
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable e) {
            Logger.error("Failed to apply forge logging fix!", e);
        }
    }*/
}
