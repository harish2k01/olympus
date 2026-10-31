package dev.olympus.desk.data
import org.json.JSONArray
import org.json.JSONObject

data class DeskWidget(val id:String,val kind:String,val target:String="",val width:Int=2,val height:Int=1,val label:String="",val fields:List<String> = emptyList(),val x:Int=-1,val y:Int=-1)
object DeskLayout {
 val kinds=listOf("Clock","Attention","CPU","Memory","Nodes","Monitors","GitOps","Downloads","Backups","Events","Alerts","Service","App","Node")
 fun defaults(wide:Boolean=true):List<DeskWidget> {
  fun w(kind:String,x:Int,y:Int,width:Int=2,height:Int=1)=DeskWidget("home-$kind",kind,width=width,height=height,x=x,y=y)
  return if(wide)listOf(w("Clock",0,0,height=2),w("Attention",2,0),w("GitOps",4,0),w("CPU",2,1),w("Memory",4,1),w("Nodes",0,2),w("Monitors",2,2),w("Events",4,2))
   else listOf(w("Clock",0,0,height=2),w("GitOps",2,0),w("Nodes",2,1),w("Attention",0,2,width=4),w("CPU",0,3),w("Memory",2,3),w("Monitors",0,4,width=4),w("Events",0,5,width=4))
 }

 fun encode(items:List<DeskWidget>)=JSONArray().apply{items.forEach{w->put(JSONObject().put("id",w.id).put("kind",w.kind).put("target",w.target).put("width",w.width).put("height",w.height).put("label",w.label).put("fields",JSONArray(w.fields)).put("x",w.x).put("y",w.y))}}.toString()
 fun decode(value:String):List<DeskWidget>{val array=JSONArray(value);require(array.length()<=40);val result=(0 until array.length()).map{i->val o=array.getJSONObject(i);val kind=o.getString("kind");require(kind in kinds);val old=o.optInt("size",1);val w=o.optInt("width",if(old==2&&kind!="Clock")4 else 2);val h=o.optInt("height",if(old==2)2 else 1);val x=o.optInt("x",-1);val y=o.optInt("y",-1);require(w in 1..6&&h in 1..4&&x in -1..5&&y in -1..80);val f=o.optJSONArray("fields")?:JSONArray();DeskWidget(o.getString("id"),kind,o.optString("target"),w,h,o.optString("label").take(40),(0 until f.length()).take(12).map{f.getString(it).take(40)},x,y)};require(result.map{it.id}.distinct().size==result.size);return result}
 data class Cell(val widget:DeskWidget,val column:Int,val row:Int,val width:Int,val height:Int)
 fun place(items:List<DeskWidget>,columns:Int):List<Cell>{
  require(columns in 1..6);val occupied=mutableSetOf<Pair<Int,Int>>()
  return items.map{item->val width=item.width.coerceIn(if(columns<=4)2.coerceAtMost(columns) else 1,columns);val height=item.height.coerceIn(1,4)
   fun free(x:Int,y:Int)=x>=0&&x+width<=columns&&y>=0&&(y until y+height).all{r->(x until x+width).all{c->(c to r) !in occupied}}
   var x=item.x.coerceAtMost(columns-width);var y=item.y
   if(!free(x,y)){x=0;y=0;while(!free(x,y)){x++;if(x+width>columns){x=0;y++}}}
   for(r in y until y+height)for(c in x until x+width)occupied.add(c to r)
   Cell(item,x,y,width,height)
  }
 }
 fun settle(items:List<DeskWidget>,columns:Int)=place(items,columns).map{it.widget.copy(width=it.width,x=it.column,y=it.row)}
 fun resize(items:List<DeskWidget>,id:String,width:Int,height:Int,columns:Int):List<DeskWidget>{val item=items.find{it.id==id}?:return items;return settle(listOf(item.copy(width=width.coerceIn(if(columns<=4)2 else 1,columns),height=height.coerceIn(1,4)))+items.filter{it.id!=id},columns)}
 fun move(items:List<DeskWidget>,id:String,x:Int,y:Int,columns:Int):List<DeskWidget>{val item=items.find{it.id==id}?:return items;return settle(listOf(item.copy(x=x.coerceIn(0,(columns-item.width).coerceAtLeast(0)),y=y.coerceIn(0,80)))+items.filter{it.id!=id},columns)}
}
