package com.snake;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Run UI creation on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            new GameWindow();
        });
    }
}
