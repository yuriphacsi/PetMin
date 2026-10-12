package com.yuri.petbite.gaming

import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

// PETMIN NOTA: geometría 3D original reutilizable, sin recursos externos ni motor pesado.
// OpenGL ES 1.x basta para una habitación y dos modelos de pocas piezas.
// PETMIN FUTURO: sustituir este renderer por modelos glTF sin cambiar progreso ni desafíos.
class PetScene(context: Context) : GLSurfaceView(context) {
    val scene = PetRenderer()
    init { setRenderer(scene); renderMode = RENDERMODE_CONTINUOUSLY }
}
class PetRenderer : GLSurfaceView.Renderer {
    @Volatile var cat = false
    @Volatile var angle = 0f
    @Volatile var animation = "idle"
    @Volatile var animationUntil = 0L
    fun animate(value: String) { animation = value; animationUntil = SystemClock.uptimeMillis() + 2200 }
    private val vertices = floatArrayOf(
        -1f,-1f,1f, 1f,-1f,1f, 1f,1f,1f, -1f,1f,1f,
        -1f,-1f,-1f, 1f,-1f,-1f, 1f,1f,-1f, -1f,1f,-1f)
    private val faces = byteArrayOf(0,1,2,0,2,3, 5,4,7,5,7,6, 4,0,3,4,3,7, 1,5,6,1,6,2, 3,2,6,3,6,7, 4,5,1,4,1,0)
    private val vb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
    private val ib = ByteBuffer.allocateDirect(faces.size).apply { put(faces); position(0) }
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        gl.glClearColor(.90f,.95f,.96f,1f); gl.glEnable(GL10.GL_DEPTH_TEST)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
    }
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        gl.glViewport(0,0,width,height); gl.glMatrixMode(GL10.GL_PROJECTION); gl.glLoadIdentity()
        GLU.gluPerspective(gl, 42f, width.toFloat()/height.coerceAtLeast(1), .1f, 30f)
    }
    private fun box(gl: GL10,x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        gl.glPushMatrix(); gl.glTranslatef(x,y,z); gl.glScalef(sx,sy,sz)
        gl.glColor4f(r,g,b,1f); gl.glVertexPointer(3,GL10.GL_FLOAT,0,vb)
        gl.glDrawElements(GL10.GL_TRIANGLES, faces.size,GL10.GL_UNSIGNED_BYTE,ib); gl.glPopMatrix()
    }
    override fun onDrawFrame(gl: GL10) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
        gl.glMatrixMode(GL10.GL_MODELVIEW); gl.glLoadIdentity(); GLU.gluLookAt(gl,3.4f,2.7f,5.4f,0f,.7f,0f,0f,1f,0f)
        box(gl,0f,-.1f,0f,2.5f,.1f,2f,.72f,.82f,.79f)
        box(gl,0f,1.3f,-2f,2.5f,1.4f,.06f,.80f,.88f,.91f)
        box(gl,-2.5f,1.3f,0f,.06f,1.4f,2f,.86f,.91f,.92f)
        box(gl,0f,.02f,0f,1.35f,.02f,1.1f,.48f,.65f,.64f)
        box(gl,.1f,.13f,1.12f,.35f,.13f,.28f,.18f,.42f,.53f)
        val now = SystemClock.uptimeMillis(); val phase = now / 180.0
        val mode = if(now < animationUntil) animation else "idle"
        gl.glPushMatrix(); gl.glRotatef(angle,-0f,1f,0f)
        val bounce = if(mode == "happy") (sin(phase)*.12).toFloat() else (sin(phase*.3)*.015).toFloat()
        gl.glTranslatef(0f,bounce,0f)
        val r = if(cat) .65f else .68f; val g = if(cat) .68f else .43f; val b = if(cat) .73f else .25f
        box(gl,0f,.65f,0f,.42f,.35f,.65f,r,g,b)
        for(x in listOf(-.28f,.28f)) for(z in listOf(-.4f,.4f)) box(gl,x,.28f,z,.12f,.27f,.13f,r*.85f,g*.85f,b*.85f)
        gl.glPushMatrix(); gl.glTranslatef(0f,1.1f,.55f)
        if(mode == "eat") gl.glRotatef(22f + (sin(phase)*12).toFloat(),1f,0f,0f)
        box(gl,0f,0f,0f,.38f,.34f,.32f,r,g,b)
        for(x in listOf(-.26f,.26f)) {
            box(gl,x,if(cat) .4f else -.03f,if(cat) 0f else -.03f,if(cat) .11f else .13f,if(cat) .22f else .38f,.12f,r*.8f,g*.8f,b*.8f)
            box(gl,x*.65f,.08f,.327f,.055f,.065f,.025f,.06f,.08f,.1f)
        }
        box(gl,0f,-.12f,.36f,.18f,.12f,.12f,.87f,.78f,.65f)
        box(gl,0f,-.08f,.49f,.065f,.045f,.02f,.12f,.1f,.1f)
        gl.glPopMatrix()
        gl.glPushMatrix(); gl.glTranslatef(0f,.8f,-.63f); gl.glRotatef((sin(phase)*if(mode=="happy") 40 else 12).toFloat(),0f,1f,0f)
        box(gl,0f,.13f,-.28f,.07f,.09f,.38f,r,g,b); gl.glPopMatrix()
        gl.glPopMatrix()
    }
}
