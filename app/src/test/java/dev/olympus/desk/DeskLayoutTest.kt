package dev.olympus.desk
import dev.olympus.desk.data.*
import org.junit.Assert.*
import org.junit.Test
class DeskLayoutTest {
 private fun noOverlap(items:List<DeskWidget>,columns:Int){val used=mutableSetOf<Pair<Int,Int>>();for(c in DeskLayout.place(items,columns)){assertTrue(c.column+c.width<=columns);for(y in c.row until c.row+c.height)for(x in c.column until c.column+c.width)assertTrue(used.add(x to y))}}
 @Test fun mixedSpansNeverOverlap(){for(columns in listOf(4,6)){val widgets=(0..35).map{DeskWidget("$it","CPU",width=1+it%6,height=1+it%4)};noOverlap(widgets,columns)}}
 @Test fun widthAndHeightResizeIndependently(){val initial=DeskLayout.defaults();val wide=DeskLayout.resize(initial,"home-CPU",4,1,6);assertEquals(4,wide.first().width);assertEquals(1,wide.first().height);val tall=DeskLayout.resize(wide,"home-CPU",4,3,6);assertEquals(4,tall.first().width);assertEquals(3,tall.first().height);noOverlap(tall,6)}
 @Test fun movingIntoOccupiedCellsPreservesEveryWidget(){val initial=DeskLayout.settle(DeskLayout.defaults(),6);val moved=DeskLayout.move(initial,"home-CPU",0,0,6);assertEquals(initial.map{it.id}.toSet(),moved.map{it.id}.toSet());assertEquals(0,moved.first().x);assertEquals(0,moved.first().y);noOverlap(moved,6)}
 @Test fun portraitClampsWideWidgetsWithoutLosingData(){val initial=DeskLayout.defaults().map{it.copy(width=6)};noOverlap(initial,4);assertTrue(DeskLayout.place(initial,4).all{it.width==4})}
 @Test fun approvedHomeFitsLandscapeThreeRows(){val cells=DeskLayout.place(DeskLayout.defaults(true),6);assertEquals(3,cells.maxOf{it.row+it.height});assertEquals(0,cells.first{it.widget.kind=="Clock"}.column);noOverlap(DeskLayout.defaults(true),6)}
 @Test fun portraitAttentionSpansScreen(){val cells=DeskLayout.place(DeskLayout.defaults(false),4);assertEquals(4,cells.first{it.widget.kind=="Attention"}.width);noOverlap(DeskLayout.defaults(false),4)}
}
