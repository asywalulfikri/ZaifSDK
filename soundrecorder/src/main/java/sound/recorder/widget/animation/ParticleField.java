package sound.recorder.widget.animation;


import java.util.List;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

class ParticleField extends View {

    private volatile List<Particle> mParticles;

    public ParticleField(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ParticleField(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ParticleField(Context context) {
        super(context);
    }

    public void setParticles(List<Particle> particles) {
        mParticles = particles;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // Draw all the particles using a local reference to avoid mid-loop replacement issues
        List<Particle> particles = mParticles;
        if (particles == null) return;

        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            if (p != null) {
                p.draw(canvas);
            }
        }
    }
}
