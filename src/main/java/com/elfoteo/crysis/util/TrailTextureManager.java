package com.elfoteo.crysis.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages a single 512×512×384 R16 3D “trail” texture in world‐space.
 * Handles allocation, compute‐shader setup, shifting, world‐offset tracking,
 * and (now) binding/unbinding for rendering.
 */
public class TrailTextureManager {
    // ---------------------------------------------------------
    // Texture IDs and size
    // ---------------------------------------------------------
    private int trailTextureId     = -1;
    private int tempTrailTextureId = -1;
    private final int trailTexWidth  = 512;
    private final int trailTexHeight = 512;
    private final int trailTexDepth  = 384;

    // The origin (in blocks) of the “(0,0,0)” texel:
    private int worldOffsetX = 0;
    private int worldOffsetY = 0;
    private int worldOffsetZ = 0;

    // Has the shader / texture been initialized yet this session?
    private boolean textureInitialized = false;

    // ---------------------------------------------------------
    // Compute‐shader objects
    // ---------------------------------------------------------
    private int shiftComputeShader  = -1;
    private int shiftComputeProgram = -1;

    // ---------------------------------------------------------
    // Timing (per‐RenderType)
    // ---------------------------------------------------------
    private final Map<Object /* RenderType */, Long> lastCallTimes    = new HashMap<>();
    private final Map<Object /* RenderType */, Float> cachedDeltaTimes = new HashMap<>();

    // ---------------------------------------------------------
    // Saved GL state (for binding/unbinding)
    // ---------------------------------------------------------
    private int prevActiveTextureUnit = GL13.GL_TEXTURE0;
    private int prevTex2D             = 0;
    private int prevTex3D             = 0;

    public TrailTextureManager() {
        // No‐arg constructor; fields are already initialized above.
    }

    // ----------------------------------------------------------------
    // PUBLIC API: getters for mixin to upload uniforms
    // ----------------------------------------------------------------

    /** Current world‐space X offset (in blocks) that corresponds to texel (0,*,*). */
    public int getWorldOffsetX() {
        return worldOffsetX;
    }

    /** Current world‐space Y offset (in blocks) that corresponds to texel (*,0,*). */
    public int getWorldOffsetY() {
        return worldOffsetY;
    }

    /** Current world‐space Z offset (in blocks) that corresponds to texel (*,*,0). */
    public int getWorldOffsetZ() {
        return worldOffsetZ;
    }

    /**
     * Returns the delta time (in seconds) since the last call for this RenderType.
     * The first time this is called for a given RenderType, returns 0.0f.
     */
    public float getDeltaTimeFor(Object renderType) {
        long currentTime = System.nanoTime();
        Long lastTime = lastCallTimes.get(renderType);

        if (lastTime == null) {
            lastCallTimes.put(renderType, currentTime);
            cachedDeltaTimes.put(renderType, 0.0f);
            return 0.0f;
        }

        float deltaSeconds = (currentTime - lastTime) / 1_000_000_000.0f;
        lastCallTimes.put(renderType, currentTime);
        cachedDeltaTimes.put(renderType, deltaSeconds);
        return deltaSeconds;
    }

    /**
     * Ensure the 3D trail texture exists (and is cleared). Also initializes
     * worldOffsetX/Y/Z so that the camera starts centered in a 512×512×384 region.
     * Must be called once before you attempt to shift or render.
     */
    public void allocateOrResizeIfNeeded() {
        int winW = Minecraft.getInstance().getWindow().getWidth();
        int winH = Minecraft.getInstance().getWindow().getHeight();

        // If already allocated and window size hasn’t changed, do nothing.
        if (trailTextureId != -1) {
            return;
        }

        // If an old texture exists, delete it so we start fresh.
        if (trailTextureId != -1) {
            GL11.glDeleteTextures(trailTextureId);
            trailTextureId = -1;
        }
        if (tempTrailTextureId != -1) {
            GL11.glDeleteTextures(tempTrailTextureId);
            tempTrailTextureId = -1;
        }

        // -------------------------------------------------------------
        // Save old binding of TEXTURE_3D
        // -------------------------------------------------------------
        prevTex3D = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        // -------------------------------------------------------------
        // Allocate new 512×512×384 R16 3D texture
        // -------------------------------------------------------------
        trailTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, trailTextureId);

        GL12.glTexImage3D(
                GL12.GL_TEXTURE_3D,
                0,
                GL30.GL_R16,            // single‐channel 16‐bit
                trailTexWidth,
                trailTexHeight,
                trailTexDepth,
                0,
                GL11.GL_RED,            // read/write as red channel
                GL11.GL_UNSIGNED_SHORT, // 16-bit data
                (ByteBuffer) null       // null → zero‐initialized (if GL4.4 is missing)
        );
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

        // Attempt to clear to zero using glClearTexImage (requires GL4.4+)
        try {
            GL44.glClearTexImage(trailTextureId, 0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (ByteBuffer)null);
        } catch (Throwable e) {
            // If it fails, we trust that the null upload zero‐initialized it anyway.
        }

        // -------------------------------------------------------------
        // Restore previous TEXTURE_3D binding
        // -------------------------------------------------------------
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D);

        // -------------------------------------------------------------
        // Compute initial worldOffset so the camera is at the center
        // -------------------------------------------------------------
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int camBlockX = (int) Math.floor(camPos.x + 64);
        int camBlockY = (int) Math.floor(camPos.y + 64);
        int camBlockZ = (int) Math.floor(camPos.z + 64);

        worldOffsetX = camBlockX - (trailTexWidth  / 2);  // 256 = 512/2
        worldOffsetY = camBlockY - (trailTexDepth  / 2);  // 192 = 384/2
        worldOffsetZ = camBlockZ - (trailTexHeight / 2);  // 256 = 512/2

        textureInitialized = false;

        // Finally, initialize the compute shader.
        initializeComputeShader();
    }

    /**
     * Call every frame (before rendering) to see if we need to “shift” the texture
     * based on how far the camera moved. If so, we dispatch the compute shader
     * to shift content, swap out the main texture, and update worldOffsetX/Y/Z.
     */
    public void updateTexturePosition(Vec3 currentCameraPos) {
        if (!textureInitialized) {
            // On first call, we do not shift—just mark as initialized.
            textureInitialized = true;
            return;
        }

        // Compute where the center of our 512×512×384 region is, in block coords:
        int textureCenterX = worldOffsetX + (trailTexWidth  / 2);
        int textureCenterY = worldOffsetY + (trailTexDepth  / 2);
        int textureCenterZ = worldOffsetZ + (trailTexHeight / 2);

        int camBlockX = (int) Math.floor(currentCameraPos.x + 64);
        int camBlockY = (int) Math.floor(currentCameraPos.y + 64);
        int camBlockZ = (int) Math.floor(currentCameraPos.z + 64);

        int deltaX = camBlockX - textureCenterX;
        int deltaY = camBlockY - textureCenterY;
        int deltaZ = camBlockZ - textureCenterZ;

        final int SHIFT_THRESHOLD = 64;
        int shiftX = 0, shiftY = 0, shiftZ = 0;
        boolean needsShift = false;

        if (Math.abs(deltaX) > SHIFT_THRESHOLD) {
            shiftX = (deltaX > 0 ? 64 : -64);
            needsShift = true;
        }
        if (Math.abs(deltaY) > SHIFT_THRESHOLD) {
            shiftY = (deltaY > 0 ? 64 : -64);
            needsShift = true;
        }
        if (Math.abs(deltaZ) > SHIFT_THRESHOLD) {
            shiftZ = (deltaZ > 0 ? 64 : -64);
            needsShift = true;
        }

        if (needsShift) {
            // Note: we supply −shiftX etc. to move texels “toward” the camera.
            shiftTextureContent(-shiftX, -shiftY, -shiftZ);

            // Now update world‐offset to reflect that the “window” has moved.
            worldOffsetX += shiftX;
            worldOffsetY += shiftY;
            worldOffsetZ += shiftZ;
        }
    }

    // ----------------------------------------------------------------
    // PRIVATE: compute‐shader initialization + shifting logic
    // ----------------------------------------------------------------

    private void initializeComputeShader() {
        if (shiftComputeProgram != -1) return; // already done

        String computeShaderSource = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;

            layout(binding = 0, r16) uniform image3D srcTexture;
            layout(binding = 1, r16) uniform image3D dstTexture;

            uniform ivec3 shiftOffset;
            uniform ivec3 textureSize;

            void main() {
                ivec3 dstCoord = ivec3(gl_GlobalInvocationID);

                if (any(greaterThanEqual(dstCoord, textureSize))) {
                    return;
                }

                ivec3 srcCoord = dstCoord - shiftOffset;
                float value = 0.0;
                if (all(greaterThanEqual(srcCoord, ivec3(0))) &&
                    all(lessThan(srcCoord, textureSize))) {
                    value = imageLoad(srcTexture, srcCoord).r;
                }
                imageStore(dstTexture, dstCoord, vec4(value, 0.0, 0.0, 0.0));
            }
            """;

        // 1. Compile compute shader
        shiftComputeShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shiftComputeShader, computeShaderSource);
        GL20.glCompileShader(shiftComputeShader);

        if (GL20.glGetShaderi(shiftComputeShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shiftComputeShader);
            throw new RuntimeException("Compute shader compilation failed: " + log);
        }

        // 2. Link into a program
        shiftComputeProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shiftComputeProgram, shiftComputeShader);
        GL20.glLinkProgram(shiftComputeProgram);

        if (GL20.glGetProgrami(shiftComputeProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(shiftComputeProgram);
            throw new RuntimeException("Compute shader linking failed: " + log);
        }
    }

    private void shiftTextureContent(int deltaX, int deltaY, int deltaZ) {
        if (shiftComputeProgram == -1) {
            System.err.println("Compute shader not initialized—cannot shift texture.");
            return;
        }

        // Save current GL state
        int prevProgram    = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevTex3D_0    = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        try {
            // If our “temp” texture doesn’t exist yet, create it now
            if (tempTrailTextureId == -1) {
                tempTrailTextureId = GL11.glGenTextures();
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, tempTrailTextureId);
                GL12.glTexImage3D(
                        GL12.GL_TEXTURE_3D, 0, GL30.GL_R16,
                        trailTexWidth, trailTexHeight, trailTexDepth,
                        0, GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (ByteBuffer)null
                );
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            }

            // 1. Use compute shader
            GL20.glUseProgram(shiftComputeProgram);

            // 2. Bind “old” texture to unit 0 (read) and “temp” to unit 1 (write)
            GL42.glBindImageTexture(
                    0,
                    trailTextureId,
                    0,
                    true,
                    0,
                    GL15.GL_READ_ONLY,
                    GL30.GL_R16
            );
            GL42.glBindImageTexture(
                    1,
                    tempTrailTextureId,
                    0,
                    true,
                    0,
                    GL15.GL_WRITE_ONLY,
                    GL30.GL_R16
            );

            // 3. Upload uniforms: shiftOffset and textureSize
            int shiftOffsetLoc = GL20.glGetUniformLocation(shiftComputeProgram, "shiftOffset");
            int textureSizeLoc = GL20.glGetUniformLocation(shiftComputeProgram, "textureSize");
            if (shiftOffsetLoc != -1) {
                GL20.glUniform3i(shiftOffsetLoc, deltaX, deltaY, deltaZ);
            }
            if (textureSizeLoc != -1) {
                GL20.glUniform3i(textureSizeLoc, trailTexWidth, trailTexHeight, trailTexDepth);
            }

            // 4. Dispatch compute
            int groupsX = (trailTexWidth  + 7) / 8;
            int groupsY = (trailTexHeight + 7) / 8;
            int groupsZ = (trailTexDepth  + 7) / 8;
            GL43.glDispatchCompute(groupsX, groupsY, groupsZ);

            // 5. Memory barrier so writes are visible
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

            // 6. Swap texture IDs (temp becomes main, main becomes temp)
            int tmp = trailTextureId;
            trailTextureId = tempTrailTextureId;
            tempTrailTextureId = tmp;
        } catch (Exception e) {
            System.err.println("Error during texture shifting: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Restore previous GL state
            GL20.glUseProgram(prevProgram);
            GL11.glBindTexture(GL12.GL_TEXTURE_BINDING_3D, prevTex3D_0);

            // Unbind image units 0 and 1
            GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16);
            GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R16);
        }
    }

    // ---------------------------------------------------------
    // INTERNAL: expose texture ID so that mixin can bind it
    // ---------------------------------------------------------
    public int getTrailTextureId() {
        return trailTextureId;
    }


    // ---------------------------------------------------------
    // NEW: Bind/unbind logic for the mixin to call
    // ---------------------------------------------------------

    /**
     * Saves the current GL state (active texture unit, bound 2D, bound 3D),
     * then binds the trailTextureId as image unit 1 (read/write) and as sampler 2,
     * and uploads the “TrailSampler” uniform to point at sampler unit 2.
     */
    public void bindForRender(ShaderInstance shaderInstance) {
        // 1) Save the old active texture unit:
        prevActiveTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        // 2) Save the old TEXTURE_2D binding (unit 0) and TEXTURE_3D binding (unit 2):
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        prevTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        prevTex3D = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);

        // 3) Bind our 3D texture to image unit 1 for read/write
        GL42.glBindImageTexture(
                1,
                this.trailTextureId,
                0,
                true,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_R16
        );

        // 4) Also bind it to sampler unit 2
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.trailTextureId);

        // 5) Upload the “TrailSampler” uniform (so the shader samples from unit 2)
        var trailSampler = shaderInstance.getUniform("TrailSampler");
        if (trailSampler != null) {
            trailSampler.set(2);
            trailSampler.upload();
        }

        // 6) Restore active texture unit back to whatever it was before:
        GL13.glActiveTexture(prevActiveTextureUnit);
    }

    /**
     * Unbinds the image unit 1 (so it no longer points at trailTextureId),
     * restores the old TEXTURE_3D binding on unit 2 and the old TEXTURE_2D on unit 0,
     * and then re‐activates whatever texture unit was active before.
     */
    public void unbindAfterRender() {
        // 1) Unbind image unit 1:
        GL42.glBindImageTexture(
                1,
                0,
                0,
                false,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_R16
        );

        // 2) Restore the old sampler binding (unit 2):
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, prevTex3D);

        // 3) Restore the old 2D texture binding (unit 0):
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2D);

        // 4) Finally, restore whichever texture unit was active before bindForRender():
        GL13.glActiveTexture(prevActiveTextureUnit);
    }
}
