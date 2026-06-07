package com.snake;

import javax.swing.JFrame;

public class GameWindow extends JFrame {
    public GameWindow() {
        this.setTitle("Neon Snake Game");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(false);
        
        // Add GamePanel
        GamePanel gamePanel = new GamePanel();
        this.add(gamePanel);
        this.pack(); // Size the frame to fit the panel
        
        this.setLocationRelativeTo(null); // Center the window
        this.setVisible(true);
    }
}
