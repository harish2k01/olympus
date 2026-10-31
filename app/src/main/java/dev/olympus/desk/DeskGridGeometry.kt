package dev.olympus.desk

/** Expand a fitting grid into the space released by Focus mode; longer grids still scroll. */
internal fun deskRowHeight(viewport:Float,gap:Float,rows:Int,wide:Boolean,fillViewport:Boolean):Float {
 val base=if(wide)((viewport-gap*2)/3).coerceIn(92f,112f)else 112f
 if(!fillViewport||rows<=0)return base
 return if(wide&&rows<=3)((viewport-gap*(rows-1))/rows).coerceAtLeast(76f) else maxOf(base,(viewport-gap*(rows-1))/rows)
}
