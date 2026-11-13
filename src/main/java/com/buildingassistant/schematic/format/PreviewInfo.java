package com.buildingassistant.schematic.format;

import javax.annotation.Nullable;

/**
 * How a schematic is shown in previews. Previews are always rendered from the schematic's own blocks;
 * this only stores the camera angle that shows the build best.
 *
 * @param image optional pre-rendered image file name, null when the preview is rendered live
 * @param yaw   camera rotation around the vertical axis, degrees
 * @param pitch camera tilt, degrees
 */
public record PreviewInfo(@Nullable String image, float yaw, float pitch) {

    public static final PreviewInfo DEFAULT = new PreviewInfo(null, 215.0F, 25.0F);
}
