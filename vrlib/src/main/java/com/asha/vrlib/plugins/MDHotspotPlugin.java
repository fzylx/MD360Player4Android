package com.asha.vrlib.plugins;

import android.content.Context;
import android.graphics.RectF;
import android.net.Uri;
import android.opengl.GLES20;
import android.util.SparseArray;

import com.asha.vrlib.MD360Director;
import com.asha.vrlib.MD360Program;
import com.asha.vrlib.MDVRLibrary;
import com.asha.vrlib.common.VRUtil;
import com.asha.vrlib.model.MDHotspotBuilder;
import com.asha.vrlib.model.MDPosition;
import com.asha.vrlib.model.MDRay;
import com.asha.vrlib.model.MDVector3D;
import com.asha.vrlib.objects.MDAbsObject3D;
import com.asha.vrlib.objects.MDObject3DHelper;
import com.asha.vrlib.objects.MDPlane;
import com.asha.vrlib.texture.MD360BitmapTexture;
import com.asha.vrlib.texture.MD360Texture;

import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.List;

import static com.asha.vrlib.common.GLUtil.glCheck;
import static com.asha.vrlib.common.VRUtil.sNotHit;

/**
 * Created by hzqiujiadi on 16/8/2.
 * hzqiujiadi ashqalcn@gmail.com
 */
public class MDHotspotPlugin extends MDAbsPlugin implements IMDHotspot{

    private static final String TAG = "MDSimplePlugin";

    private MDVRLibrary.ITouchPickListener clickListener;

    private MDAbsObject3D object3D;

    private MD360Program program;

    private SparseArray<Uri> uriList;

    private RectF size;

    private String title;

    private int mPendingTextureKey = 0;

    private int mCurrentTextureKey = 0;

    private MD360Texture texture;

    private MDVRLibrary.IImageLoadProvider provider;

    public MDHotspotPlugin(MDHotspotBuilder builder) {
        provider = builder.imageLoadProvider;
        uriList = builder.uriList;
        size = new RectF(0, 0, builder.width, builder.height);
        clickListener = builder.clickListener;
        setTitle(builder.title);
        setModelPosition(builder.position == null ? MDPosition.getOriginalPosition() : builder.position);
    }

    @Override
    public void initInGL(Context context) {

        program = new MD360Program(MDVRLibrary.ContentType.BITMAP);
        program.build(context);

        object3D = new MDPlane(size);
        MDObject3DHelper.loadObj(context,object3D);

        texture = new MD360BitmapTexture(new MDVRLibrary.IBitmapProvider() {
            @Override
            public void onProvideBitmap(MD360BitmapTexture.Callback callback) {
                Uri uri = uriList.get(mCurrentTextureKey);
                if (uri != null){
                    provider.onProvideBitmap(uri, callback);
                }
            }
        });
        texture.create();
    }

    @Override
    public void beforeRenderer(int totalWidth, int totalHeight) {

    }

    @Override
    public void renderer(int index, int width, int height, MD360Director director) {
        if (texture == null){
            return;
        }

        if (mPendingTextureKey != mCurrentTextureKey){
            mCurrentTextureKey = mPendingTextureKey;
            texture.notifyChanged();
        }

        texture.texture(program);

        if (texture.isReady()){
            // Update Projection
            director.updateViewport(width, height);

            // Set our per-vertex lighting program.
            program.use();
            glCheck("MDSimplePlugin mProgram use");

            object3D.uploadVerticesBufferIfNeed(program, index);

            object3D.uploadTexCoordinateBufferIfNeed(program, index);

            // Pass in the combined matrix.
            director.shot(program, getModelPosition(), true);

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);


            object3D.draw();
            GLES20.glDisable(GLES20.GL_BLEND);
        }

    }

    @Override
    public void destroyInGL() {

    }

    @Override
    protected boolean removable() {
        return true;
    }

    @Override
    public float hit(MDRay ray) {
        if (object3D == null || object3D.getVerticesBuffer(0) == null){
            return sNotHit;
        }

        MDPosition position = getModelPosition();
        float[] model = position.getMatrix();

        List<MDVector3D> points = new LinkedList<>();

        FloatBuffer buffer = object3D.getVerticesBuffer(0);
        int numPoints = buffer.capacity() / 3;

        for (int i = 0; i < numPoints; i++){
            MDVector3D v = new MDVector3D();
            v.setX(buffer.get(i * 3)).setY(buffer.get(i * 3 + 1)).setZ(buffer.get(i * 3 + 2));
            v.multiplyMV(model);
            points.add(v);
        }
        float hit1 = sNotHit;
        float hit2 = sNotHit;
        if (points.size() == 4){
            hit1 = VRUtil.intersectTriangle(ray, points.get(0), points.get(1), points.get(2));
            hit2 = VRUtil.intersectTriangle(ray,points.get(1), points.get(2), points.get(3));
        }

        return Math.min(hit1,hit2);
    }

    @Override
    public void onEyeHitIn(long timestamp) {

    }

    @Override
    public void onEyeHitOut() {

    }

    @Override
    public void onTouchHit(MDRay ray) {
        if (clickListener != null){
            clickListener.onHotspotHit(this, ray);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void useTexture(int key) {
        mPendingTextureKey = key;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
