package ex1.app;

import ex1.core.Vec3;

/**
 * Orbit camera with an orthographic projection, in world coordinates (millimetres, +Z = up; see
 * {@link VolumeGeometry}). Yaw turns around the vertical axis, pitch raises the camera; yaw 0,
 * pitch 0 looks at the face of a patient (from anterior towards posterior). The same basis gives
 * screen positions for lines ({@link #project}) and the rays of the volume renderer
 * ({@link #rayOrigin}, {@link #forward}).
 */
final class Camera3D
{

    // ==========================================
    //             CONSTANTS & FIELDS
    // ==========================================

    /** Radians per degree. */
    private static final double DEG = Math.PI / 180.0;

    /** The pitch stops short of straight up and down, where "up" on the screen would flip. */
    private static final double MIN_PITCH = -89.0;
    private static final double MAX_PITCH = 89.0;

    /** The start view, turned and tilted so that all three axes show. */
    static final double DEFAULT_YAW   = -35.0;
    static final double DEFAULT_PITCH = 20.0;

    /** Limits of the zoom, in pixels per world unit. */
    private static final double MIN_ZOOM = 0.02;
    private static final double MAX_ZOOM = 80.0;

    /** A fitted box's diagonal spans this share of the view's shorter side. */
    private static final double FIT_SHARE = 0.82;

    /** lookFrom: a view steeper than this keeps its turn; the tilt stays within LOOK_PITCH. */
    private static final double STEEP      = 80.0;
    private static final double LOOK_PITCH = 75.0;

    /** Where the camera is: angles in degrees around the centre, and pixels per world unit. */
    private double yaw    = DEFAULT_YAW;
    private double pitch  = DEFAULT_PITCH;
    private double zoom   = 1.0;
    private Vec3   centre = new Vec3(0, 0, 0);

    /** The screen's axes in the world, worked out again whenever the angles change. */
    private Vec3 right;
    private Vec3 up;
    private Vec3 forward;

    /** Starts at the default angles. */
    Camera3D()
    {
        updateBasis();
    }

    /**
     * Sets the point the camera turns around.
     * @param (Vec3) c World point the camera orbits around.
     */
    void setCentre(Vec3 c)
    {
        centre = c;
    }

    /**
     * Copies the camera.
     * @return (Camera3D) An independent copy: a frame drawn on another thread keeps the camera it
     *                    was asked with, whatever the mouse does meanwhile.
     */
    Camera3D copy()
    {
        Camera3D c = new Camera3D();
        c.yaw = yaw;
        c.pitch = pitch;
        c.zoom = zoom;
        c.centre = centre;
        c.updateBasis();
        return c;
    }

    // ==========================================
    //                   MOVES
    // ==========================================

    /**
     * Turns the camera around its centre; the pitch stops short of straight up or down.
     * @param (double) dYaw Degrees to add to the yaw.
     * @param (double) dPitch Degrees to add to the pitch.
     */
    void orbit(double dYaw, double dPitch)
    {
        yaw += dYaw;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + dPitch));
        updateBasis();
    }

    /**
     * Zooms in or out, within limits.
     * @param (double) factor Multiplier for the zoom (> 1 = closer).
     */
    void zoomBy(double factor)
    {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
    }

    /**
     * Chooses a zoom that fits a box of the given diagonal into the viewport.
     * @param (double) diagonal Length to fit, in world units.
     * @param (int) viewWidth Viewport width.
     * @param (int) viewHeight Viewport height.
     */
    void fit(double diagonal, int viewWidth, int viewHeight)
    {
        zoom = FIT_SHARE * Math.min(viewWidth, viewHeight) / Math.max(1.0, diagonal);
    }

    /**
     * Sets yaw and pitch directly (the pitch is clamped).
     * @param (double) newYaw Yaw in degrees.
     * @param (double) newPitch Pitch in degrees.
     */
    void setAngles(double newYaw, double newPitch)
    {
        yaw = newYaw;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, newPitch));
        updateBasis();
    }

    /**
     * Puts the camera on the given side of the scene, a little turned and raised so the view
     * stays three-dimensional.
     * @param (Vec3) towardsViewer World direction from the scene towards the camera.
     * @param (double) extraYaw Degrees added to the yaw.
     * @param (double) extraPitch Degrees added to the pitch.
     */
    void lookFrom(Vec3 towardsViewer, double extraYaw, double extraPitch)
    {
        Vec3   v = towardsViewer.normalized();
        double p = Math.asin(Math.max(-1.0, Math.min(1.0, v.z))) / DEG;
        double y = Math.atan2(v.x, -v.y) / DEG;
        if (Math.abs(p) > STEEP)
        {
            // Looking straight down or up: keep the current turn.
            y = yaw;
        }
        setAngles(y + extraYaw, Math.max(-LOOK_PITCH, Math.min(LOOK_PITCH, p + extraPitch)));
    }

    // ==========================================
    //                 PROJECTION
    // ==========================================

    /**
     * Gives the direction from the scene back towards the camera.
     * @return (Vec3) Unit vector from the scene towards the camera.
     */
    Vec3 towardsViewer()
    {
        return forward.scale(-1);
    }

    /**
     * Gives the current zoom.
     * @return (double) Pixels per world unit.
     */
    double zoom()
    {
        return zoom;
    }

    /**
     * Projects a world point to screen space.
     * @param (Vec3) p World point.
     * @param (int) viewWidth Viewport width.
     * @param (int) viewHeight Viewport height.
     * @param (double) u Texture column to carry along.
     * @param (double) v Texture row to carry along.
     * @return (Raster.Vertex) Screen vertex; depth grows away from the viewer.
     */
    Raster.Vertex project(Vec3 p, int viewWidth, int viewHeight, double u, double v)
    {
        Vec3   d  = p.sub(centre);
        double sx = viewWidth / 2.0 + d.dot(right) * zoom;
        double sy = viewHeight / 2.0 - d.dot(up) * zoom;
        return new Raster.Vertex(sx, sy, d.dot(forward) * zoom, u, v);
    }

    /**
     * Finds where a pixel's ray crosses the plane through the centre facing the camera; the ray is
     * rayOrigin + t * forward(), and the depth of its point at t is t * zoom().
     * @param (double) sx Screen x (pixel centre).
     * @param (double) sy Screen y.
     * @param (int) viewWidth Viewport width.
     * @param (int) viewHeight Viewport height.
     * @return (Vec3) World point.
     */
    Vec3 rayOrigin(double sx, double sy, int viewWidth, int viewHeight)
    {
        double x = (sx - viewWidth / 2.0) / zoom;
        double y = (viewHeight / 2.0 - sy) / zoom;
        return centre.add(right.scale(x)).add(up.scale(y));
    }

    /**
     * Gives the direction the camera looks in.
     * @return (Vec3) Unit view direction (from the viewer into the scene).
     */
    Vec3 forward()
    {
        return forward;
    }

    /**
     * Gives the direction of the screen's x axis in the world.
     * @return (Vec3) Unit screen-right vector.
     */
    Vec3 right()
    {
        return right;
    }

    /**
     * Gives the direction of the screen's y axis (upwards) in the world.
     * @return (Vec3) Unit screen-up vector.
     */
    Vec3 up()
    {
        return up;
    }

    // ==========================================
    //             LOW-LEVEL HELPERS
    // ==========================================

    /** Recomputes the orthonormal basis from yaw and pitch. */
    private void updateBasis()
    {
        double y             = yaw * DEG;
        double p             = pitch * DEG;
        Vec3   towardsViewer = new Vec3(Math.cos(p) * Math.sin(y), -Math.cos(p) * Math.cos(y), Math.sin(p));
        forward = towardsViewer.scale(-1);
        right = new Vec3(Math.cos(y), Math.sin(y), 0);
        up = right.cross(forward).normalized();
    }
}
