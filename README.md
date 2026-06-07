# Project Log: Neon Snake

This project documents the development of a highly responsive, modern Swing-based Snake client. The core objective was to take a classic arcade concept and elevate it into a premium desktop experience by optimizing rendering pipelines, decoupling update logic from rendering ticks, and building custom visual effect routines.

---

## Developer Log & Architectural Notes

### 1. The Rendering Loop & Threading model
Standard Swing-based games often fall into the trap of tying both physics and rendering to a single, slow timer (e.g., 100–150ms). This yields an extremely choppy ~7 FPS refresh rate, rendering smooth animations or particles impossible. 

To resolve this, this project implements a **Dual-Loop Accumulator Model**:
* **Animation Loop**: A Swing timer fires continuously at 16ms intervals (~60 FPS), driving particles, floating text drifts, menu fade animations, and trigonometric size calculations.
* **Physics / Game Step**: Inside the 60 FPS update, a time accumulator (`moveCooldown`) counts down. When it expires, a single discrete snake movement is executed. This permits the snake to move at its structured speed (starting at 130ms and accelerating dynamically) while all visual assets render at a fluid 60 FPS.

### 2. Particle Physics System
To make interactions satisfying, a simple Euler-integration particle system was written. Consuming food triggers a burst of 20 randomized particle entities.
* Each particle receives a random radial velocity vector.
* Velocity is multiplied by a friction coefficient (0.95) on every frame to simulate air resistance, causing the explosion to expand rapidly and then settle.
* Opacity decays linearly based on remaining lifespan, drawing fading circular arcs.

### 3. Visual Polish & Geometry Interpolation
* **Tapered Snake Body**: To avoid blocky rectangles, each segment is drawn as a rounded rectangle. The segment size is dynamically interpolated from the head down to the tail (scaling down from 25px to 18px), giving the snake an organic, tapered shape.
* **Eyes Rendering**: The snake head renders character eyes containing white sclera and black pupils. The placement of the pupils is context-aware, translating offset coordinates based on the snake's directional state ('U', 'D', 'L', 'R').
* **Pulsating Food**: Rather than drawing a static circle, the apple size is modeled using a sine-wave function: `18 + sin(tick * 0.3) * 3`. This creates a breathing pulse effect.
* **HUD Protection**: The apple spawn grid is offset by `UNIT_SIZE` on the Y-axis. This leaves the top row of the screen dedicated to the HUD, preventing gameplay assets from overlapping with the text.

### 4. Linux Buffer Syncing
On Linux desktop environments (particularly X11/Mutter/KWin), Swing applications suffer from micro-stuttering due to rendering pipeline queuing. To force immediate frame delivery, the rendering loop invokes:
```java
Toolkit.getDefaultToolkit().sync();
```
This flushes the native graphics system's display queues, guaranteeing consistent frame times on Linux host systems.

---

## Technical Specifications

* **Language**: Java 17 (compiled via release target 17 to prevent system module warnings)
* **GUI Toolkit**: Java AWT / Swing (Double-buffered JPanels)
* **Build Tool**: Apache Maven 3.x
* **State Management**: Finite State Machine (Menu, Playing, Paused, Game Over states)
* **Persistent Storage**: Disk-persisted high scores via flat-file stream (`highscore.txt`)

---

## Running the Application

### Compilation
To compile the source files and generate binary classes:
```bash
mvn clean compile
```

### Execution
To execute the entry-point class:
```bash
mvn exec:java
```

### Packaging
To compile, run test structures, and package into a target jar:
```bash
mvn clean package
```

---

## Controls Layout

| Input Keys | Action mapped |
| --- | --- |
| **W, A, S, D** or **Arrow Keys** | Steer snake direction (Up, Left, Down, Right) |
| **SPACE** or **ENTER** | Start / Restart game from screen overlays |
| **SPACE** or **P** | Toggle Pause state |
| **ESCAPE** | Reset game state to Start Menu (from Pause / Game Over) |

---

## Project Structure

```
java-maven-snake-game/
├── pom.xml               # Maven configuration and dependency management
├── .gitignore            # Version control exclusions (target build, score state)
├── README.md             # Programmer development log
└── src/
    └── main/
        └── java/
            └── com/
                └── snake/
                    ├── Main.java         # EDT initialization wrapper
                    ├── GameWindow.java   # JFrame instantiation and centering
                    └── GamePanel.java    # Engine core, loop handlers, state machine, graphics
```
