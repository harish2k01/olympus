package dev.olympus.desk
import org.junit.Assert.*
import org.junit.Test
class DeskGridGeometryTest {
 @Test fun focusFillsReleasedSpaceInEitherOrientation(){for(wide in listOf(true,false)){val rows=if(wide)3 else 6;val viewport=if(wide)412f else 820f;val unit=deskRowHeight(viewport,10f,rows,wide,true);assertEquals(viewport,unit*rows+10f*(rows-1),.01f)}}
 @Test fun focusUsesActualLayoutRowCount(){for(rows in 1..4){val unit=deskRowHeight(500f,12f,rows,true,true);assertEquals(500f,unit*rows+12f*(rows-1),.01f)}}
 @Test fun overflowingLayoutKeepsReadableRowsAndScrolls(){assertEquals(112f,deskRowHeight(400f,10f,8,true,true),.01f);assertEquals(112f,deskRowHeight(700f,10f,8,false,true),.01f)}
 @Test fun regularAndEmptyGridsKeepTheirBaseSize(){assertEquals(112f,deskRowHeight(412f,10f,3,true,false),.01f);assertEquals(112f,deskRowHeight(412f,10f,0,true,true),.01f)}
}
