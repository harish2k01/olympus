package dev.olympus.desk
import dev.olympus.desk.data.*
import org.junit.Assert.*
import org.junit.Test

class DeskLayoutTest {
 @Test fun layoutsNeverOverlapOrLoseWidgets(){for(columns in listOf(2,4)){val items=(0..20).map{DeskWidget("$it","CPU",size=it%3)};val rows=DeskLayout.rows(items,columns);assertEquals(items,rows.flatten());assertTrue(rows.all{row->row.sumOf{DeskLayout.span(it,columns)}<=columns})}}
 @Test fun largeWidgetsUseTwoCells(){assertEquals(2,DeskLayout.span(DeskWidget("x","Memory",size=2),4));assertEquals(1,DeskLayout.span(DeskWidget("y","CPU",size=0),2))}
 @Test fun tallClockPacksWithoutOverlap(){for(columns in listOf(2,3)){val cells=DeskLayout.place(DeskLayout.defaults(),columns);val occupied=mutableSetOf<Pair<Int,Int>>();for(cell in cells){assertTrue(cell.column+cell.width<=columns);for(y in cell.row until cell.row+cell.height)for(x in cell.column until cell.column+cell.width)assertTrue(occupied.add(x to y))};assertEquals(if(columns==2)2 else 4,cells.first().height)}}
}
