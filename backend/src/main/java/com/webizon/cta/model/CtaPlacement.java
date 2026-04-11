package com.webizon.cta.model;

/**
 * Where on the room layout a CTA is rendered.
 *
 * <p>Placement is the unit of competition for CTAs. Two CTAs with the
 * same placement fight for the same screen real estate, and
 * {@code CtaResolver} resolves the conflict by priority (or by stacking
 * if every CTA in that placement permits it).
 *
 * <ul>
 *   <li>{@link #INLINE} — between the video and the chat, always visible</li>
 *   <li>{@link #SIDEBAR} — to the side of the video on desktop</li>
 *   <li>{@link #POPUP} — modal dialog; never stacks</li>
 *   <li>{@link #BELOW_VIDEO} — sticky ribbon under the video</li>
 * </ul>
 */
public enum CtaPlacement {
    INLINE,
    SIDEBAR,
    POPUP,
    BELOW_VIDEO
}
