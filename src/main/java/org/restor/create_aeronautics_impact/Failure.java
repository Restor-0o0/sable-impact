package org.restor.create_aeronautics_impact;

/**
 * How a material gives way, which is a different question from how strong it is.
 *
 * <p>Strength alone says when a block breaks and nothing about what the shock does afterwards, and those
 * are the two halves of why a crash reads as a structure failing rather than as a sphere of deletion. A pane
 * of glass and a steel beam of the same nominal strength behave nothing alike: the pane goes at a touch and
 * absorbs the touch, the beam takes an enormous amount and hands most of it on to whatever it is bolted to.
 *
 * <p>Kept free of Minecraft on purpose, so the arithmetic that uses it can be tested without one.
 */
public enum Failure {

    /** Glass, ice, lamps. Fails at a fraction of its strength and swallows what broke it. */
    BRITTLE,

    /** Metal, wood, wool. Takes a great deal before it fails, and absorbs while it holds. */
    DUCTILE,

    /** Stone and anything bearing load. Fails at its strength and stops carrying once it has. */
    STRUCTURAL
}
