package com.factionclash.logic.hero;

/**
 * The procedural lag of a hero's chains (HEROES_V2 4.4 "Procedural touches"; the chains and their stiffness and damping
 * come from {@code visual.json} {@code secondary}): a damped spring whose position follows a target (the body's yaw),
 * so a ponytail, tail or scarf trails a turn and swings back. The same spring as the generator's follow-through bake
 * (G3): {@code 0.8 + 3.5 x stiffness} Hz with a damping ratio of {@code 0.8 x damping}, so the baked and the procedural
 * motion feel alike. The animation bakes the motion inside a clip; this adds only what the clip cannot know (the
 * entity turning in the world). Pure, unit-tested ({@code SwayTest}).
 */
public final class Sway {
    /** The largest lag (degrees) a chain shows. */
    public static final double LIMIT = 25;
    /** Longer frame gaps (seconds) are simulated as this, so a hitch never throws the chain. */
    public static final double MAX_STEP = 0.05;

    private final double omega;
    private final double zeta;
    private double position;
    private double velocity;
    private boolean started;

    public Sway(double stiffness, double damping) {
        double s = clamp01(stiffness), d = clamp01(damping);
        this.omega = 2 * Math.PI * (0.8 + 3.5 * s);
        this.zeta = Math.max(0.05, 0.8 * d);
    }

    /**
     * Moves the spring {@code seconds} toward {@code target} (degrees; an angle, unwrapped against the spring's own so a
     * turn through 180 does not spin the chain) and returns the lag: how far the spring trails the target, clamped to
     * {@link #LIMIT}.
     */
    public double step(double target, double seconds) {
        if (!started || !Double.isFinite(position)) {
            position = target;
            velocity = 0;
            started = true;
            return 0;
        }
        double t = unwrap(target, position);
        double remaining = Math.max(0, Math.min(seconds, 0.25));
        while (remaining > 1e-9) {
            double dt = Math.min(MAX_STEP, remaining);
            double accel = -omega * omega * (position - t) - 2 * zeta * omega * velocity;
            velocity += accel * dt;
            position += velocity * dt;
            remaining -= dt;
        }
        double lag = position - t;
        if (Math.abs(lag) > LIMIT) {
            lag = Math.copySign(LIMIT, lag);
            position = t + lag;
            velocity = 0;
        }
        return lag;
    }

    /** The current lag without stepping. */
    public double lag(double target) {
        return started ? Math.max(-LIMIT, Math.min(LIMIT, position - unwrap(target, position))) : 0;
    }

    /** {@code angle} moved by whole turns to within 180 degrees of {@code near}. */
    static double unwrap(double angle, double near) {
        double d = angle - near;
        d -= 360 * Math.floor((d + 180) / 360);
        return near + d;
    }

    private static double clamp01(double v) {
        return Double.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0.5;
    }
}
