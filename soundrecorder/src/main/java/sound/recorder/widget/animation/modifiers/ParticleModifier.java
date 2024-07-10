package sound.recorder.widget.animation.modifiers;


import sound.recorder.widget.animation.Particle;

public interface ParticleModifier {

	/**
	 * modifies the specific value of a particle given the current miliseconds
	 * @param particle
	 * @param miliseconds
	 */
	void apply(Particle particle, long miliseconds);

}
