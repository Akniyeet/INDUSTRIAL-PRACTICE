package com.webizon.cta.model;

/**
 * The four distinct CTA kinds supported by the unified CTA system.
 *
 * <p>CLAUDE.md explicitly mandates that File, Link, Course, and Form CTAs
 * share a single table and a single rendering pipeline ("File / Button
 * / Banner = ONE unified CTA system"). The type only controls which
 * payload fields are populated and how the frontend renders the call
 * to action.
 *
 * <table>
 *   <caption>Payload expectations per type</caption>
 *   <tr><th>Type</th><th>Required field</th><th>Purpose</th></tr>
 *   <tr><td>FILE</td>   <td>{@code fileUrl}</td>   <td>Download a material (checklist, PDF)</td></tr>
 *   <tr><td>LINK</td>   <td>{@code actionUrl}</td> <td>Open an external URL</td></tr>
 *   <tr><td>COURSE</td> <td>{@code actionUrl}</td> <td>Open an EDUSER course page</td></tr>
 *   <tr><td>FORM</td>   <td>{@code actionUrl}</td> <td>Open a lead-capture form</td></tr>
 * </table>
 */
public enum CtaType {
    FILE,
    LINK,
    COURSE,
    FORM
}
