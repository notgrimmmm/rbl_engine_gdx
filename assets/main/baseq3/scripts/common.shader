textures/common/skip
{
    surfaceparm nodraw
    surfaceparm skip
}

textures/common/nodraw
{
    surfaceparm nodraw
}

textures/common/clip
{
    surfaceparm nodraw
    surfaceparm playerclip
}

textures/common/trigger
{
	surfaceparm nodraw
	surfaceparm nonsolid
}

textures/common/prototype
{
    qer_editorimage textures/common/prototype.png
	q3map_shadeAngle 360
    {
        map $lightmap
		blendFunc filter
        rgbGen identity
    }
}