package com.seibel.distanthorizons.common.backports;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
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

	/**
	 * The camera position is requested many times per frame (IE once per cloud),
	 * the offset only depends on the model view matrix, which is replaced with a new object every frame,
	 * so the matrix inversion is only done when the matrix changes.
	 */
	private DhMat4f offsetModelViewMatrix = null;
	private final Vector4f offset = new Vector4f(); // third person offset

	/** 
	 * Synchronized and returns a copy since this is called from both the
	 * render thread and DH's beacon culling thread.
	 * If there's no camera entity (IE while leaving a world) the last position is returned.
	 */
	public synchronized Vector3d getUpdatedPos(EntityLivingBase entity, float partialTicks)
	{
		if (entity != null)
		{
			this.update(entity, partialTicks);
		}
		return new Vector3d(this.pos);
	}
	
	private void update(EntityLivingBase entity, float partialTicks)
	{
		DhMat4f modelViewMatrix = ClientApi.RENDER_STATE.mcModelViewMatrix;
		if (modelViewMatrix != this.offsetModelViewMatrix)
		{
			this.offsetModelViewMatrix = modelViewMatrix;
			this.offset.set(0, 0, 0, 1);
			Matrix4f inverseModelView = modelViewMatrix
				.createJomlMatrix()
				.invert();
			inverseModelView.transform(this.offset);
		}
		Vector4f offset = this.offset;

		final double camX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks + offset.x;
		final double camY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + offset.y;
		final double camZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks + offset.z;
		this.pos.set(camX, camY, camZ);
	}
	
	public synchronized Vector3d getPos() { return new Vector3d(this.pos); }
}
