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
    private static final double DEG = Math.PI / 180.0;
    private static final double MIN_PITCH = -89.0;
    private static final double MAX_PITCH = 89.0;
    static final double DEFAULT_YAW = -35.0;
    static final double DEFAULT_PITCH = 20.0;

    private double yaw = DEFAULT_YAW;
    private double pitch = DEFAULT_PITCH;
    private double zoom = 1.0;
    private Vec3 centre = new Vec3(0, 0, 0);
    private Vec3 right;
    private Vec3 up;
    private Vec3 forward;

    /** Starts at the default angles. */
    Camera3D()
    {
        updateBasis();
    }

    /**
     * @param (Vec3) c world point the camera orbits around
     */
    void setCentre(Vec3 c)
    {
        centre = c;
    }

    /**
     * @return an independent copy: a frame drawn on another thread keeps the camera it was asked
     *         with, whatever the mouse does meanwhile
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

    /**
     * @param (double) dYaw degrees to add to the yaw
     * @param (double) dPitch degrees to add to the pitch
     */
    void orbit(double dYaw, double dPitch)
    {
        yaw += dYaw;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + dPitch));
        updateBasis();
    }

    /**
     * @param (double) factor multiplier for the zoom (> 1 = closer)
     */
    void zoomBy(double factor)
    {
        zoom = Math.max(0.02, Math.min(80.0, zoom * factor));
    }

    /**
     * Chooses a zoom that fits a box of the given diagonal into the viewport.
     *
     * @param (double) diagonal length to fit, in world units
     * @param (int) viewWidth viewport width
     * @param (int) viewHeight viewport height
     */
    void fit(double diagonal, int viewWidth, int viewHeight)
    {
        zoom = 0.82 * Math.min(viewWidth, viewHeight) / Math.max(1.0, diagonal);
    }

    /**
     * @param (double) newYaw yaw in degrees
     * @param (double) newPitch pitch in degrees
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
     *
     * @param (Vec3) towardsViewer world direction from the scene towards the camera
     * @param (double) extraYaw degrees added to the yaw
     * @param (double) extraPitch degrees added to the pitch
     */
    void lookFrom(Vec3 towardsViewer, double extraYaw, double extraPitch)
    {
        Vec3 v = towardsViewer.normalized();
        double p = Math.asin(Math.max(-1.0, Math.min(1.0, v.z))) / DEG;
        double y = Math.atan2(v.x, -v.y) / DEG;
        if (Math.abs(p) > 80.0)
        {
            // Looking straight down or up: keep the current turn.
            y = yaw;
        }
        setAngles(y + extraYaw, Math.max(-75.0, Math.min(75.0, p + extraPitch)));
    }

    /**
     * @return unit vector from the scene towards the camera
     */
    Vec3 towardsViewer()
    {
        return forward.scale(-1);
    }

    /**
     * @return pixels per world unit
     */
    double zoom()
    {
        return zoom;
    }

    /**
     * Projects a world point to screen space.
     *
     * @param (Vec3) p world point
     * @param (int) viewWidth viewport width
     * @param (int) viewHeight viewport height
     * @param (double) u texture column to carry along
     * @param (double) v texture row to carry along
     * @return screen vertex; depth grows away from the viewer
     */
    Raster.Vertex project(Vec3 p, int viewWidth, int viewHeight, double u, double v)
    {
        Vec3 d = p.sub(centre);
        double sx = viewWidth / 2.0 + d.dot(right) * zoom;
        double sy = viewHeight / 2.0 - d.dot(up) * zoom;
        return new Raster.Vertex(sx, sy, d.dot(forward) * zoom, u, v);
    }

    /**
     * The point of a pixel's ray on the plane through the centre facing the camera; the ray is
     * rayOrigin + t * forward(), and the depth of its point at t is t * zoom().
     *
     * @param (double) sx screen x (pixel centre)
     * @param (double) sy screen y
     * @param (int) viewWidth viewport width
     * @param (int) viewHeight viewport height
     * @return world point
     */
    Vec3 rayOrigin(double sx, double sy, int viewWidth, int viewHeight)
    {
        double x = (sx - viewWidth / 2.0) / zoom;
        double y = (viewHeight / 2.0 - sy) / zoom;
        return centre.add(right.scale(x)).add(up.scale(y));
    }

    /**
     * @return unit view direction (from the viewer into the scene)
     */
    Vec3 forward()
    {
        return forward;
    }

    /**
     * @return unit screen-right vector
     */
    Vec3 right()
    {
        return right;
    }

    /**
     * @return unit screen-up vector
     */
    Vec3 up()
    {
        return up;
    }

    /** Recomputes the orthonormal basis from yaw and pitch. */
    private void updateBasis()
    {
        double y = yaw * DEG;
        double p = pitch * DEG;
        Vec3 towardsViewer = new Vec3(Math.cos(p) * Math.sin(y), -Math.cos(p) * Math.cos(y), Math.sin(p));
        forward = towardsViewer.scale(-1);
        right = new Vec3(Math.cos(y), Math.sin(y), 0);
        up = right.cross(forward).normalized();
    }
}
