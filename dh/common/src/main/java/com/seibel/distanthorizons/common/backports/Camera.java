package com.seibel.distanthorizons.common.backports;

import com.seibel.distanthorizons.core.util.math.DhVec3d;
import net.minecraft.entity.EntityLivingBase;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

/**
 * Recreation of MC's 1.12+ camera object.
 */
public class Camera
{
	public static final Camera INSTANCE = new Camera();

	private final Vector3d pos = new Vector3d();

	/** the camera's offset from the camera entity, IE third person */
	private final Vector4f offset = new Vector4f(0, 0, 0, 1);

	/**
	 * Called with MC's model view matrix every frame. <br>
	 * On 1.7.10 that matrix also moves the camera away from the camera entity (IE third person),
	 * the offset is taken out of it here. DH renders relative to the camera itself,
	 * so it gets the matrix without that translation, otherwise the offset would be applied twice.
	 * The inversion is only done once a frame since the position is requested many times (IE once per cloud).
	 */
	public synchronized void setModelViewMatrix(Matrix4f fullModelViewMatrix)
	{
		this.offset.set(0, 0, 0, 1);
		new Matrix4f(fullModelViewMatrix).invert().transform(this.offset);
	}

	/**
	 * Synchronized and fills the given vector since this is called from both the
	 * render thread and DH's beacon culling thread.
	 * If there's no camera entity (IE while leaving a world) the last position is returned.
	 */
	/** fills the given vector with the camera's offset from the camera entity, IE third person */
	public synchronized void getOffsetFromEntity(DhVec3d output) { output.set(this.offset.x, this.offset.y, this.offset.z); }

	public synchronized void getUpdatedPos(EntityLivingBase entity, float partialTicks, DhVec3d output)
	{
		if (entity != null)
		{
			this.update(entity, partialTicks);
		}
		output.set(this.pos.x, this.pos.y, this.pos.z);
	}

	private void update(EntityLivingBase entity, float partialTicks)
	{
		Vector4f offset = this.offset;

		final double camX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks + offset.x;
		final double camY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + offset.y;
		final double camZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks + offset.z;
		this.pos.set(camX, camY, camZ);
	}
}
