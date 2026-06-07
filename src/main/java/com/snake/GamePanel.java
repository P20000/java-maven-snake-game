package com.snake;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.AlphaComposite;
import java.awt.Toolkit;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener {

    private static final int SCREEN_WIDTH = 600;
    private static final int SCREEN_HEIGHT = 600;
    private static final int UNIT_SIZE = 25;
    private static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    private static final int INITIAL_DELAY = 130;
    private static final int FPS_DELAY = 16; // ~60 FPS animation/render loop

    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private final int[] prevX = new int[GAME_UNITS];
    private final int[] prevY = new int[GAME_UNITS];
    private int bodyParts;
    private int score;
    private int highScore;
    
    private int appleX;
    private int appleY;
    
    private char direction = 'R';
    private char nextDirection = 'R';
    
    private boolean running = false;
    private Timer timer;
    private final Random random;

    private int snakeMoveDelay = INITIAL_DELAY;
    private int moveCooldown = 0;
    
    private enum GameState {
        START,
        PLAYING,
        PAUSED,
        GAME_OVER
    }
    
    private GameState state = GameState.START;
    private int animationTick = 0;
    
    // Particle and Animation Systems
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();

    public GamePanel() {
        random = new Random();
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(new Color(18, 18, 28));
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter());
        
        // Load High Score
        highScore = loadHighScore();
        
        // Initialize and start timer running at 60 FPS for menu and animation updates
        timer = new Timer(FPS_DELAY, this);
        timer.start();
    }

    private void startGame() {
        bodyParts = 3;
        score = 0;
        direction = 'R';
        nextDirection = 'R';
        
        // Center the snake
        int startGridX = 12;
        int startGridY = 12;
        for (int i = 0; i < bodyParts; i++) {
            x[i] = (startGridX - i) * UNIT_SIZE;
            y[i] = startGridY * UNIT_SIZE;
            prevX[i] = x[i];
            prevY[i] = y[i];
        }
        
        particles.clear();
        floatingTexts.clear();
        
        generateApple();
        
        snakeMoveDelay = INITIAL_DELAY;
        moveCooldown = snakeMoveDelay;
        
        state = GameState.PLAYING;
        running = true;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        
        // Turn on antialiasing for smooth graphics and text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // 1. Draw Background dot grid
        drawGrid(g2d);
        
        // 2. Draw Game Elements if in playing, paused, or game over state
        if (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.GAME_OVER) {
            drawApple(g2d);
            drawSnake(g2d);
            drawParticles(g2d);
            drawFloatingTexts(g2d);
            drawHUD(g2d);
        }
        
        // 3. Draw State-Specific Overlays
        switch (state) {
            case START:
                drawStartScreen(g2d);
                break;
            case PAUSED:
                drawPausedScreen(g2d);
                break;
            case GAME_OVER:
                drawGameOverScreen(g2d);
                break;
            default:
                break;
        }
        
        // Sync the graphics state (critical for smooth animations on Linux/X11)
        Toolkit.getDefaultToolkit().sync();
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 10)); // Very subtle grid dots
        for (int i = 0; i < SCREEN_WIDTH; i += UNIT_SIZE) {
            for (int j = 0; j < SCREEN_HEIGHT; j += UNIT_SIZE) {
                g2d.fillOval(i + UNIT_SIZE / 2 - 1, j + UNIT_SIZE / 2 - 1, 2, 2);
            }
        }
    }

    private void drawApple(Graphics2D g2d) {
        // Pulsate apple size using a sine wave
        int sizeOffset = (int) (Math.sin(animationTick * 0.3) * 3);
        int size = 18 + sizeOffset;
        int offset = (UNIT_SIZE - size) / 2;
        
        // Draw Apple Glow
        g2d.setColor(new Color(231, 76, 60, 45));
        g2d.fillOval(appleX + offset - 4, appleY + offset - 4, size + 8, size + 8);
        
        // Draw Apple Body (Bright Red)
        g2d.setColor(new Color(231, 76, 60));
        g2d.fillOval(appleX + offset, appleY + offset, size, size);
        
        // Draw Stem
        g2d.setColor(new Color(139, 69, 19));
        g2d.fillRect(appleX + UNIT_SIZE / 2 - 1, appleY + offset - 3, 2, 4);
        
        // Draw Leaf
        g2d.setColor(new Color(46, 204, 113));
        g2d.fillOval(appleX + UNIT_SIZE / 2 + 1, appleY + offset - 4, 4, 3);
        
        // Draw Glossy Highlight
        g2d.setColor(new Color(255, 255, 255, 180));
        g2d.fillOval(appleX + offset + size / 4, appleY + offset + size / 4, size / 5, size / 5);
    }

    private void drawSnake(Graphics2D g2d) {
        // Calculate interpolation factor t [0.0, 1.0]
        double t = 1.0;
        if (state == GameState.PLAYING && running) {
            t = (double) (snakeMoveDelay - moveCooldown) / snakeMoveDelay;
            if (t > 1.0) t = 1.0;
            if (t < 0.0) t = 0.0;
        }

        Stroke oldStroke = g2d.getStroke();

        // 1. Draw connecting slime body segments (from tail to neck)
        for (int i = bodyParts - 1; i > 0; i--) {
            // Interpolated coordinates for segment i (center)
            int cx = (int) Math.round(prevX[i] + t * (x[i] - prevX[i])) + UNIT_SIZE / 2;
            int cy = (int) Math.round(prevY[i] + t * (y[i] - prevY[i])) + UNIT_SIZE / 2;

            // Interpolated coordinates for segment i-1 (center)
            int px = (int) Math.round(prevX[i-1] + t * (x[i-1] - prevX[i-1])) + UNIT_SIZE / 2;
            int py = (int) Math.round(prevY[i-1] + t * (y[i-1] - prevY[i-1])) + UNIT_SIZE / 2;

            // Taper the snake body towards the tail
            float factor = (float) i / (bodyParts > 1 ? bodyParts - 1 : 1);
            int segmentSize = UNIT_SIZE - (int) (factor * 7); // size from 25 down to 18
            
            // Neon Green to Neon Blue-Teal Gradient
            int r = (int) (46 + factor * (52 - 46));
            int gCol = (int) (204 + factor * (152 - 204));
            int b = (int) (113 + factor * (219 - 113));
            Color color = new Color(r, gCol, b);

            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(segmentSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(cx, cy, px, py);
        }

        // 2. Draw the head on top
        if (bodyParts > 0) {
            int currentX = (int) Math.round(prevX[0] + t * (x[0] - prevX[0]));
            int currentY = (int) Math.round(prevY[0] + t * (y[0] - prevY[0]));

            g2d.setColor(new Color(46, 204, 113)); // Bright neon green
            g2d.fillOval(currentX, currentY, UNIT_SIZE, UNIT_SIZE);

            // Draw Character Eyes depending on direction
            g2d.setColor(Color.WHITE);
            int eyeSize = 6;
            int pupilSize = 3;
            int eye1X = 0, eye1Y = 0, eye2X = 0, eye2Y = 0;
            int pupil1X = 0, pupil1Y = 0, pupil2X = 0, pupil2Y = 0;
            
            switch (direction) {
                case 'U':
                    eye1X = currentX + 4;               eye1Y = currentY + 4;
                    eye2X = currentX + UNIT_SIZE - 10;  eye2Y = currentY + 4;
                    pupil1X = eye1X + 1;            pupil1Y = eye1Y + 1;
                    pupil2X = eye2X + 1;            pupil2Y = eye2Y + 1;
                    break;
                case 'D':
                    eye1X = currentX + 4;               eye1Y = currentY + UNIT_SIZE - 10;
                    eye2X = currentX + UNIT_SIZE - 10;  eye2Y = currentY + UNIT_SIZE - 10;
                    pupil1X = eye1X + 1;            pupil1Y = eye1Y + 3;
                    pupil2X = eye2X + 1;            pupil2Y = eye2Y + 3;
                    break;
                case 'L':
                    eye1X = currentX + 4;               eye1Y = currentY + 4;
                    eye2X = currentX + 4;               eye2Y = currentY + UNIT_SIZE - 10;
                    pupil1X = eye1X + 1;            pupil1Y = eye1Y + 1;
                    pupil2X = eye2X + 1;            pupil2Y = eye2Y + 1;
                    break;
                case 'R':
                    eye1X = currentX + UNIT_SIZE - 10;  eye1Y = currentY + 4;
                    eye2X = currentX + UNIT_SIZE - 10;  eye2Y = currentY + UNIT_SIZE - 10;
                    pupil1X = eye1X + 3;            pupil1Y = eye1Y + 1;
                    pupil2X = eye2X + 3;            pupil2Y = eye2Y + 1;
                    break;
            }
            g2d.fillOval(eye1X, eye1Y, eyeSize, eyeSize);
            g2d.fillOval(eye2X, eye2Y, eyeSize, eyeSize);
            g2d.setColor(Color.BLACK);
            g2d.fillOval(pupil1X, pupil1Y, pupilSize, pupilSize);
            g2d.fillOval(pupil2X, pupil2Y, pupilSize, pupilSize);
        }

        g2d.setStroke(oldStroke);
    }

    private void drawParticles(Graphics2D g2d) {
        for (Particle p : particles) {
            p.draw(g2d);
        }
    }

    private void drawFloatingTexts(Graphics2D g2d) {
        for (FloatingText ft : floatingTexts) {
            ft.draw(g2d);
        }
    }

    private void drawHUD(Graphics2D g2d) {
        g2d.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2d.setColor(new Color(255, 255, 255, 180));
        g2d.drawString("SCORE: " + score, 20, 25);
        g2d.drawString("BEST: " + highScore, SCREEN_WIDTH - 110, 25);
    }

    private void drawStartScreen(Graphics2D g2d) {
        // Semi-transparent dimming overlay
        g2d.setColor(new Color(10, 10, 15, 220));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        // Retro Logo / Title
        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
        g2d.setColor(new Color(46, 204, 113)); // Neon Green
        drawCenteredString(g2d, "NEON SNAKE", SCREEN_HEIGHT / 2 - 80);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2d.setColor(new Color(255, 255, 255, 150));
        drawCenteredString(g2d, "Dodge walls, eat apples, grow long.", SCREEN_HEIGHT / 2 - 30);
        
        // Pulsing instruction prompt
        float promptAlpha = 0.6f + 0.4f * (float) Math.sin(animationTick * 0.15);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2d.setColor(new Color(52, 152, 219, (int) (promptAlpha * 255))); // Neon Blue
        drawCenteredString(g2d, "PRESS [SPACE] TO START", SCREEN_HEIGHT / 2 + 50);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2d.setColor(new Color(255, 255, 255, 100));
        drawCenteredString(g2d, "Use W-A-S-D or Arrow Keys to navigate", SCREEN_HEIGHT / 2 + 100);
        drawCenteredString(g2d, "Press P or SPACE to Pause", SCREEN_HEIGHT / 2 + 125);
    }

    private void drawPausedScreen(Graphics2D g2d) {
        g2d.setColor(new Color(10, 10, 15, 180));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 40));
        g2d.setColor(Color.WHITE);
        drawCenteredString(g2d, "GAME PAUSED", SCREEN_HEIGHT / 2 - 40);
        
        float promptAlpha = 0.6f + 0.4f * (float) Math.sin(animationTick * 0.15);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2d.setColor(new Color(46, 204, 113, (int) (promptAlpha * 255)));
        drawCenteredString(g2d, "PRESS [SPACE] TO RESUME", SCREEN_HEIGHT / 2 + 20);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2d.setColor(new Color(255, 255, 255, 100));
        drawCenteredString(g2d, "PRESS [ESC] FOR MAIN MENU", SCREEN_HEIGHT / 2 + 60);
    }

    private void drawGameOverScreen(Graphics2D g2d) {
        g2d.setColor(new Color(20, 10, 10, 230)); // Red-tinted dimming overlay
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
        g2d.setColor(new Color(231, 76, 60)); // Crimson Red
        drawCenteredString(g2d, "GAME OVER", SCREEN_HEIGHT / 2 - 90);
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2d.setColor(Color.WHITE);
        drawCenteredString(g2d, "FINAL SCORE: " + score, SCREEN_HEIGHT / 2 - 30);
        
        if (score >= highScore && score > 0) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2d.setColor(new Color(241, 196, 15)); // Gold Color
            drawCenteredString(g2d, "★ NEW PERSONAL BEST ★", SCREEN_HEIGHT / 2);
        } else {
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g2d.setColor(new Color(255, 255, 255, 150));
            drawCenteredString(g2d, "PERSONAL BEST: " + highScore, SCREEN_HEIGHT / 2);
        }
        
        float promptAlpha = 0.6f + 0.4f * (float) Math.sin(animationTick * 0.15);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2d.setColor(new Color(46, 204, 113, (int) (promptAlpha * 255)));
        drawCenteredString(g2d, "PRESS [SPACE] TO PLAY AGAIN", SCREEN_HEIGHT / 2 + 60);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2d.setColor(new Color(255, 255, 255, 100));
        drawCenteredString(g2d, "PRESS [ESC] FOR MAIN MENU", SCREEN_HEIGHT / 2 + 100);
    }

    private void drawCenteredString(Graphics2D g2d, String text, int yPos) {
        FontMetrics metrics = g2d.getFontMetrics(g2d.getFont());
        int xPos = (SCREEN_WIDTH - metrics.stringWidth(text)) / 2;
        g2d.drawString(text, xPos, yPos);
    }

    private void generateApple() {
        boolean onSnake;
        do {
            onSnake = false;
            appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
            
            // Start apple Y coordinate from index 1 (y = UNIT_SIZE) to keep row 0 (y = 0) free for the HUD
            appleY = (random.nextInt((SCREEN_HEIGHT - UNIT_SIZE) / UNIT_SIZE) + 1) * UNIT_SIZE;
            
            for (int i = 0; i < bodyParts; i++) {
                if (x[i] == appleX && y[i] == appleY) {
                    onSnake = true;
                    break;
                }
            }
        } while (onSnake);
    }

    private void move() {
        // Save current positions as previous for interpolation
        for (int i = 0; i < bodyParts; i++) {
            prevX[i] = x[i];
            prevY[i] = y[i];
        }

        // Commit direction change
        direction = nextDirection;
        
        // Move body parts backwards
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }
        
        // Move head based on direction
        switch (direction) {
            case 'U':
                y[0] = y[0] - UNIT_SIZE;
                break;
            case 'D':
                y[0] = y[0] + UNIT_SIZE;
                break;
            case 'L':
                x[0] = x[0] - UNIT_SIZE;
                break;
            case 'R':
                x[0] = x[0] + UNIT_SIZE;
                break;
        }
    }

    private void checkApple() {
        if (x[0] == appleX && y[0] == appleY) {
            bodyParts++;
            prevX[bodyParts - 1] = x[bodyParts - 1];
            prevY[bodyParts - 1] = y[bodyParts - 1];
            score += 100;
            
            // Check for new High Score
            if (score > highScore) {
                highScore = score;
                saveHighScore(highScore);
            }
            
            // Increase speed slightly (decrease snake move delay), floor at 70ms
            if (snakeMoveDelay > 70) {
                snakeMoveDelay -= 3;
            }
            
            // Create particle explosion
            for (int i = 0; i < 15; i++) {
                particles.add(new Particle(appleX + UNIT_SIZE / 2.0, appleY + UNIT_SIZE / 2.0, new Color(231, 76, 60)));
            }
            // Spawn some green leaf particles too
            for (int i = 0; i < 5; i++) {
                particles.add(new Particle(appleX + UNIT_SIZE / 2.0, appleY + UNIT_SIZE / 2.0, new Color(46, 204, 113)));
            }
            
            // Create floating text animation
            floatingTexts.add(new FloatingText("+100", appleX + UNIT_SIZE / 4.0, appleY, new Color(46, 204, 113)));
            
            generateApple();
        }
    }

    private void checkCollisions() {
        // Check if head collides with own body
        for (int i = bodyParts; i > 0; i--) {
            if (x[0] == x[i] && y[0] == y[i]) {
                running = false;
                break;
            }
        }
        
        // Check if head touches left border
        if (x[0] < 0) {
            running = false;
        }
        // Check if head touches right border
        if (x[0] >= SCREEN_WIDTH) {
            running = false;
        }
        // Check if head touches top border
        if (y[0] < 0) {
            running = false;
        }
        // Check if head touches bottom border
        if (y[0] >= SCREEN_HEIGHT) {
            running = false;
        }
        
        if (!running) {
            state = GameState.GAME_OVER;
            
            // Explode the entire snake into neon blue/green particles
            for (int i = 0; i < bodyParts; i++) {
                for (int p = 0; p < 6; p++) {
                    particles.add(new Particle(x[i] + UNIT_SIZE / 2.0, y[i] + UNIT_SIZE / 2.0, new Color(52, 152, 219)));
                    particles.add(new Particle(x[i] + UNIT_SIZE / 2.0, y[i] + UNIT_SIZE / 2.0, new Color(46, 204, 113)));
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animationTick++;
        
        if (state == GameState.PLAYING && running) {
            moveCooldown -= FPS_DELAY;
            if (moveCooldown <= 0) {
                move();
                checkApple();
                checkCollisions();
                moveCooldown = snakeMoveDelay;
            }
        }
        
        // Update particles and floating texts
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update();
            if (p.isDead()) {
                particles.remove(i);
            }
        }
        for (int i = floatingTexts.size() - 1; i >= 0; i--) {
            FloatingText ft = floatingTexts.get(i);
            ft.update();
            if (ft.isDead()) {
                floatingTexts.remove(i);
            }
        }
        
        repaint();
    }

    private int loadHighScore() {
        File file = new File("highscore.txt");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                return Integer.parseInt(reader.readLine().trim());
            } catch (Exception ex) {
                // Fail silently and return 0
            }
        }
        return 0;
    }

    private void saveHighScore(int val) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("highscore.txt"))) {
            writer.print(val);
        } catch (Exception ex) {
            // Fail silently
        }
    }

    // KeyAdapter Inner Class
    private class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();
            
            if (state == GameState.START) {
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    startGame();
                }
            } else if (state == GameState.PLAYING) {
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    if (direction != 'R') {
                        nextDirection = 'L';
                    }
                }
                if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    if (direction != 'L') {
                        nextDirection = 'R';
                    }
                }
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    if (direction != 'D') {
                        nextDirection = 'U';
                    }
                }
                if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    if (direction != 'U') {
                        nextDirection = 'D';
                    }
                }
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_P) {
                    state = GameState.PAUSED;
                    repaint();
                }
            } else if (state == GameState.PAUSED) {
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_P) {
                    state = GameState.PLAYING;
                    repaint();
                } else if (key == KeyEvent.VK_ESCAPE) {
                    state = GameState.START;
                    repaint();
                }
            } else if (state == GameState.GAME_OVER) {
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    startGame();
                } else if (key == KeyEvent.VK_ESCAPE) {
                    state = GameState.START;
                    repaint();
                }
            }
        }
    }

    // Particle Inner Class
    private static class Particle {
        double x, y;
        double dx, dy;
        int life;
        int maxLife;
        Color color;

        public Particle(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            double angle = Math.random() * 2 * Math.PI;
            double speed = 1.0 + Math.random() * 4.0;
            this.dx = Math.cos(angle) * speed;
            this.dy = Math.sin(angle) * speed;
            this.maxLife = 15 + (int) (Math.random() * 15);
            this.life = this.maxLife;
            this.color = color;
        }

        public void update() {
            x += dx;
            y += dy;
            dx *= 0.95;
            dy *= 0.95;
            life--;
        }

        public boolean isDead() {
            return life <= 0;
        }

        public void draw(Graphics2D g) {
            float alpha = (float) life / maxLife;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)));
            int size = 3 + (int) (alpha * 5);
            g.fillOval((int) x - size / 2, (int) y - size / 2, size, size);
        }
    }

    // Floating Text Inner Class
    private static class FloatingText {
        String text;
        double x, y;
        double dy;
        int life;
        int maxLife;
        Color color;

        public FloatingText(String text, double x, double y, Color color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.dy = -1.0 - Math.random() * 1.0;
            this.maxLife = 30 + (int) (Math.random() * 15);
            this.life = this.maxLife;
            this.color = color;
        }

        public void update() {
            y += dy;
            life--;
        }

        public boolean isDead() {
            return life <= 0;
        }

        public void draw(Graphics2D g) {
            float alpha = (float) life / maxLife;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(text, (int) x, (int) y);
        }
    }
}
