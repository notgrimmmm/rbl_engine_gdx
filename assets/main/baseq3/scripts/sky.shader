textures/sky/sky
{
	q3map_sunExt 0.3 0.3 0.3 35 360 80 3 16 // r g b intensity degrees elevation deviance samples
	q3map_lightmapFilterRadius 0 8 // get rid of stadium lights
	q3map_skylight 100 3 // brightness plus iterations for uniform lighting
	
	surfaceparm sky
	surfaceparm noimpact
	surfaceparm nolightmap
	surfaceparm nodlight
	
	nopicmip
	nomipmaps
}