import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class TempoSupremo extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JLabel timeLabel = new JLabel("00:00:00.000", SwingConstants.CENTER);

    private final javax.swing.Timer uiTimer;
    private final javax.swing.Timer colorTimer; // timer que altera a cor automaticamente
    private long startNano = 0L;
    private long accumulatedNanos = 0L;
    private boolean running = false;

    private Point mouseDownCompCoords = null;
    private static final float FONT_SIZE_STEP = 2.0f;

    // Constantes para chaves de preferências e ações para evitar erros de digitação
    private static final String PREF_AUTO_COLORS = "autoColors";
    private static final String ACTION_MARK_LAP = "markLap";

    // controle de matiz (hue) para cores cíclicas
    private float hue = 0f;
    private static final float HUE_STEP = 0.005f; // quanto a cor muda a cada tick
    private static final int COLOR_DELAY_MS = 80; // intervalo de atualização da cor

    // persistência de preferência do usuário
    private final Preferences prefs = Preferences.userNodeForPackage(TempoSupremo.class);

    // Lista para armazenar as voltas
    private final List<String> laps = new ArrayList<>();

    // Buffer reutilizável para formatação de tempo (evita criar objetos a cada frame)
    private final StringBuilder timeBuffer = new StringBuilder(16);

    public TempoSupremo() {
        super("TempoSupremo — Cronômetro Profissional");   

        // --- Ícone da janela (LogoC.png) ---
        loadAppIcon();
        // --------------------------------------

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(0, 0, 0, 0));

        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 30f));
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setOpaque(false);
        timeLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        timeLabel.setToolTipText("Clique duplo: Iniciar/Parar | Roda do mouse: Tamanho | Botão direito: Menu");
        timeLabel.setFocusable(true); // Permite que o label receba foco do teclado
        add(timeLabel, BorderLayout.CENTER);

        uiTimer = new javax.swing.Timer(16, e -> updateDisplay());

        // Timer que altera a cor automaticamente (inicialmente criado, inicia mais abaixo conforme preferência)
        colorTimer = new javax.swing.Timer(COLOR_DELAY_MS, e -> {
            hue += HUE_STEP;
            if (hue > 1f) hue -= 1f;
            // saturação alta e brilho alto para cores vivas
            Color c = Color.getHSBColor(hue, 0.95f, 1.0f);
            timeLabel.setForeground(c);
        });

        // Respeita preferência do usuário (padrão = true)
        boolean autoColors = prefs.getBoolean(PREF_AUTO_COLORS, true);
        if (autoColors) colorTimer.start();

        // --- Comportamento de Mouse (Arrastar, Iniciar/Parar, Popup) ---
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // verifica se o clique foi para abrir o popup (compatível com todos os SOs)
                if (e.isPopupTrigger()) {
                    showPopup(e);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    timeLabel.requestFocusInWindow(); // Garante o foco para o atalho de teclado funcionar
                    mouseDownCompCoords = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // também verifica se o clique foi para abrir o popup (necessário para alguns SOs)
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
                mouseDownCompCoords = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (mouseDownCompCoords != null) {
                    Point curr = e.getLocationOnScreen();
                    setLocation(curr.x - mouseDownCompCoords.x, curr.y - mouseDownCompCoords.y);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // duplo-clique para iniciar/parar
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    toggleStartStop();
                }
            }
        };

        // --- MouseWheelListener (Alterar Tamanho da Fonte) ---
        MouseWheelListener mwl = e -> {
            float currentSize = timeLabel.getFont().getSize2D();
            float newSize;

            if (e.getWheelRotation() < 0) {
                newSize = currentSize + FONT_SIZE_STEP;
            } else {
                newSize = currentSize - FONT_SIZE_STEP;
            }

            if (newSize < 10.0f) newSize = 10.0f;
            adjustFontSize(newSize);
        };

        // Conecta listeners
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(mwl);
        timeLabel.addMouseListener(ma);
        timeLabel.addMouseMotionListener(ma);
        timeLabel.addMouseWheelListener(mwl);

        // --- Atalho de Teclado (ESPAÇO para marcar volta) ---
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), ACTION_MARK_LAP);
        am.put(ACTION_MARK_LAP, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                markLap();
            }
        });

        pack();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screen.width - getWidth() - 20, 20);
    }

    private void showPopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();

        // 1. Menu de Opções de Fonte
        JMenu fontOptions = new JMenu("Opções de Fonte");

        // 1a. Submenu para Tipo de Fonte
        JMenu fontTypeMenu = new JMenu("Tipo de Fonte");
        String[] fontNames = {"Segoe UI", "Arial", "Times New Roman", "Monospaced", "DS-Digital"};
        for (String name : fontNames) {
            JMenuItem item = new JMenuItem(name);
            item.setFont(new Font(name, Font.PLAIN, 12));
            item.addActionListener(a -> adjustFontType(name));
            fontTypeMenu.add(item);
        }
        fontOptions.add(fontTypeMenu);

        // 1b. Submenu para Cor da Fonte (mantém opção manual)
        JMenu fontColorMenu = new JMenu("Cor da Numeração");
        String[] colorNames = {"Branco", "Vermelho", "Verde", "Azul", "Amarelo"};
        Color[] colors = {Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};

        // checkbox para ativar/desativar cores automáticas (estado inicial da preferência)
        boolean autoColors = prefs.getBoolean(PREF_AUTO_COLORS, true);
        JCheckBoxMenuItem autoColorsItem = new JCheckBoxMenuItem("Cores Automáticas", autoColors);
        autoColorsItem.addActionListener(a -> {
            boolean sel = autoColorsItem.isSelected();
            if (sel) colorTimer.start(); else colorTimer.stop();
            prefs.putBoolean(PREF_AUTO_COLORS, sel);
        });
        fontOptions.add(autoColorsItem);
        fontOptions.addSeparator();

        // Opção para alternar "Sempre no Topo"
        JCheckBoxMenuItem alwaysOnTopItem = new JCheckBoxMenuItem("Sempre no Topo", isAlwaysOnTop());
        alwaysOnTopItem.addActionListener(a -> setAlwaysOnTop(alwaysOnTopItem.isSelected()));
        fontOptions.add(alwaysOnTopItem);

        for (int i = 0; i < colors.length; i++) {
            JMenuItem colorItem = new JMenuItem(colorNames[i]);
            colorItem.setIcon(createColorIcon(colors[i], 12, 12));
            final Color selectedColor = colors[i];
            colorItem.addActionListener(a -> selectManualColor(selectedColor));
            fontColorMenu.add(colorItem);
        }

        JMenuItem customColorItem = new JMenuItem("Cor Personalizada...");
        customColorItem.addActionListener(a -> {
            Color newColor = JColorChooser.showDialog(this, "Escolha uma Cor", timeLabel.getForeground());
            selectManualColor(newColor);
        });
        fontColorMenu.addSeparator();
        fontColorMenu.add(customColorItem);

        fontOptions.add(fontColorMenu);

        // Itens principais
        JMenuItem lapsItem = new JMenuItem("Ver Voltas");
        JMenuItem resetItem = new JMenuItem("Resetar");
        JMenuItem exitItem = new JMenuItem("Sair");

        lapsItem.addActionListener(a -> showLaps());
        resetItem.addActionListener(a -> reset());
        exitItem.addActionListener(a -> System.exit(0));

        // Adiciona todos ao popup
        popup.add(fontOptions);
        popup.addSeparator();
        popup.add(lapsItem);
        popup.add(resetItem);
        popup.addSeparator();
        popup.add(exitItem);

        // Exibe o popup na localização do evento do mouse
        Component invoker = (e.getComponent() != null) ? e.getComponent() : this;
        popup.show(invoker, e.getX(), e.getY());
    }

    // Método auxiliar para criar ícones de cor para o menu
    private static Icon createColorIcon(Color color, int w, int h) {
        return new ColorIcon(color, w, h);
    }

    // Método auxiliar para definir uma cor manualmente, desativando o modo automático.
    // A mudança é refletida no menu da próxima vez que ele for aberto.
    private void selectManualColor(Color newColor) {
        if (newColor == null) return;

        colorTimer.stop();
        prefs.putBoolean(PREF_AUTO_COLORS, false);
        adjustFontColor(newColor);
    }

    private void adjustFontType(String newFontName) {
        float currentSize = timeLabel.getFont().getSize2D();
        int currentStyle = timeLabel.getFont().getStyle();

        timeLabel.setFont(new Font(newFontName, currentStyle, Math.max(10, (int) currentSize)));

        pack();
    }

    private void adjustFontColor(Color newColor) {
        timeLabel.setForeground(newColor);
    }

    private void adjustFontSize(float newSize) {
        Font currentFont = timeLabel.getFont();
        timeLabel.setFont(currentFont.deriveFont(newSize));

        pack();
    }

    private void toggleStartStop() {
        if (!running) {
            startNano = System.nanoTime();
            running = true;
            uiTimer.start();
        } else {
            long now = System.nanoTime();
            accumulatedNanos += now - startNano;
            running = false;
            uiTimer.stop();
        }
    }

    private void markLap() {
        if (!running && accumulatedNanos == 0) return; // Não marca se estiver zerado
        
        String current = timeLabel.getText();
        laps.add(current);
        
        // Feedback visual rápido (piscar cor)
        Color original = timeLabel.getForeground();
        timeLabel.setForeground(Color.GRAY);
        javax.swing.Timer blink = new javax.swing.Timer(100, e -> timeLabel.setForeground(original));
        blink.setRepeats(false);
        blink.start();
    }

    private void showLaps() {
        if (laps.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhuma volta marcada ainda.\nPressione ESPAÇO para marcar.", "Voltas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < laps.size(); i++) {
            sb.append(String.format("Volta %d: %s\n", i + 1, laps.get(i)));
        }
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(textArea), "Registro de Voltas", JOptionPane.PLAIN_MESSAGE);
    }

    private void reset() {
        running = false;
        uiTimer.stop();
        accumulatedNanos = 0L;
        startNano = 0L;
        laps.clear(); // Limpa as voltas ao resetar
        updateDisplay();
    }

    private long currentElapsedNanos() {
        return accumulatedNanos + (running ? (System.nanoTime() - startNano) : 0L);
    }

    private void updateDisplay() {
        timeLabel.setText(formatDuration(currentElapsedNanos()));
    }

    private String formatDuration(long nanos) {
        long millis = nanos / 1_000_000;
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms = millis % 1000;

        // Otimização: StringBuilder é mais eficiente que String.format para atualizações frequentes (loop de UI)
        timeBuffer.setLength(0); // Limpa o buffer para reutilização
        if (hours < 10) timeBuffer.append('0');
        timeBuffer.append(hours).append(':');
        if (minutes < 10) timeBuffer.append('0');
        timeBuffer.append(minutes).append(':');
        if (seconds < 10) timeBuffer.append('0');
        timeBuffer.append(seconds).append('.');
        if (ms < 10) timeBuffer.append("00");
        else if (ms < 100) timeBuffer.append('0');
        timeBuffer.append(ms);
        return timeBuffer.toString();
    }

    private static class ColorIcon implements Icon {
        private final Color color;
        private final int w, h;

        public ColorIcon(Color color, int w, int h) {
            this.color = color;
            this.w = w;
            this.h = h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y, w, h);
        }

        @Override public int getIconWidth() { return w; }
        @Override public int getIconHeight() { return h; }
    }

    private void loadAppIcon() {
        try {
            // Recursos devem ser carregados com a barra inicial para serem absolutos no classpath.
            // Isso torna o carregamento mais previsível após o empacotamento em um JAR.
            java.net.URL iconURL = TempoSupremo.class.getResource("/LogoC.png");
            if (iconURL != null) {
                setIconImage(Toolkit.getDefaultToolkit().getImage(iconURL));
            } else {
                // Se o ícone não for encontrado, é um problema de build/pacote.
                System.err.println("Aviso: Ícone 'LogoC.png' não foi encontrado no classpath do aplicativo.");
            }
        } catch (Exception ex) {
            System.err.println("Aviso: Falha ao carregar o ícone do aplicativo. " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        SwingUtilities.invokeLater(() -> {
            try {
                TempoSupremo t = new TempoSupremo();
                t.setVisible(true);
            } catch (Throwable ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Erro ao iniciar o aplicativo:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(), "Erro Fatal", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}