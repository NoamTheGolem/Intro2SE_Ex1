package ex1.app;

import ex1.core.Vec3;

/**
 * Orbit camera with an orthographic projection: the user drags to rotate around the volume
 * centre and scrolls to zoom. Volume +y maps to screen down, so an unrotated view shows the XY
 * slice exactly as the 2-D view does.
 */
final class Camera3D
{
    private static final double DEG = Math.PI / 180.0;
    private static final double MIN_PITCH = -89.0;
    private static final double MAX_PITCH = 89.0;

    private double yaw = -35.0;
    private double pitch = 25.0;
    private double zoom = 1.0;
    private Vec3 centre = new Vec3(0, 0, 0);

    /**
     * @param (Vec3) c point the camera orbits around (the volume centre)
     */
    void setCentre(Vec3 c)
    {
        centre = c;
    }

    /**
     * @param (double) dYaw degrees to add to the yaw
     * @param (double) dPitch degrees to add to the pitch
     */
    void orbit(double dYaw, double dPitch)
    {
        yaw += dYaw;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch + dPitch));
    }

    /**
     * @param (double) factor multiplier for the zoom (> 1 = closer)
     */
    void zoomBy(double factor)
    {
        zoom = Math.max(0.05, Math.min(50.0, zoom * factor));
    }

    /**
     * Chooses a zoom that fits a box of the given diagonal into the viewport.
     *
     * @param (double) diagonal length to fit, in voxels
     * @param (int) viewWidth viewport width
     * @param (int) viewHeight viewport height
     */
    void fit(double diagonal, int viewWidth, int viewHeight)
    {
        zoom = 0.8 * Math.min(viewWidth, viewHeight) / Math.max(1.0, diagonal);
    }

    /**
     * @param (double) newYaw yaw in degrees
     * @param (double) newPitch pitch in degrees
     */
    void setAngles(double newYaw, double newPitch)
    {
        yaw = newYaw;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, newPitch));
    }

    /**
     * @return yaw in degrees
     */
    double yaw()
    {
        return yaw;
    }

    /**
     * @return pitch in degrees
     */
    double pitch()
    {
        return pitch;
    }

    /**
     * Projects a world point to screen space.
     *
     * @param (Vec3) p world point (voxel units)
     * @param (int) viewWidth viewport width
     * @param (int) viewHeight viewport height
     * @param (double) u texture column to carry along
     * @param (double) v texture row to carry along
     * @return screen vertex; depth grows away from the viewer
     */
    Raster.Vertex project(Vec3 p, int viewWidth, int viewHeight, double u, double v)
    {
        Vec3 q = p.sub(centre);
        q = PlaneSpec.rotY(q, -yaw * DEG);
        q = PlaneSpec.rotX(q, -pitch * DEG);
        double sx = viewWidth / 2.0 + q.x * zoom;
        double sy = viewHeight / 2.0 + q.y * zoom;
        return new Raster.Vertex(sx, sy, -q.z * zoom, u, v);
    }

    /**
     * @return unit vector pointing from the scene towards the viewer, in world coordinates
     */
    Vec3 towardsViewer()
    {
        Vec3 z = new Vec3(0, 0, 1);
        return PlaneSpec.rotY(PlaneSpec.rotX(z, pitch * DEG), yaw * DEG);
    }
}
