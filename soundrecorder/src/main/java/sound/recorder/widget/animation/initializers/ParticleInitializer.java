package sound.recorder.widget.animation.initializers;

import java.util.Random;

import sound.recorder.widget.animation.Particle;

public interface ParticleInitializer {
	void initParticle(Particle p, Random r);

}
