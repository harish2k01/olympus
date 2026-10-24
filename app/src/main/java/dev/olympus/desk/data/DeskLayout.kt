package dev.olympus.desk.data

import org.json.JSONArray
import org.json.JSONObject

data class DeskWidget(val id:String,val kind:String,val target:String="",val size:Int=1,val label:String="")
object DeskLayout {
    val kinds=listOf("Clock","Attention","CPU","Memory","Nodes","Monitors","GitOps","Downloads","Backups","Events","App","Node")
    fun defaults()=listOf("Clock","Nodes","GitOps","CPU","Memory").mapIndexed{i,k->DeskWidget("default-$i",k,size=if(k=="Clock")2 else 1)}
    fun encode(items:List<DeskWidget>)=JSONArray().apply{items.forEach{put(JSONObject().put("id",it.id).put("kind",it.kind).put("target",it.target).put("size",it.size).put("label",it.label))}}.toString()
    fun decode(value:String):List<DeskWidget>{val list=JSONArray(value);require(list.length()<=40);val result=(0 until list.length()).map{val item=list.getJSONObject(it);val size=item.getInt("size");val kind=item.getString("kind");require(kind in kinds&&size in 0..2);DeskWidget(item.getString("id"),kind,item.optString("target"),size,item.optString("label").take(40))};require(result.map{it.id}.distinct().size==result.size);return result}
    // Pack rows without overlapping; large cards occupy an entire row on phones.
    fun rows(items:List<DeskWidget>,columns:Int):List<List<DeskWidget>>{val out=mutableListOf<List<DeskWidget>>();var row=mutableListOf<DeskWidget>();var used=0;for(item in items){val span=span(item,columns);if(used+span>columns){out.add(row);row=mutableListOf();used=0};row.add(item);used+=span};if(row.isNotEmpty())out.add(row);return out}
    fun span(item:DeskWidget,columns:Int)=if(item.size==2)2.coerceAtMost(columns) else 1
    data class Cell(val widget:DeskWidget,val column:Int,val row:Int,val width:Int,val height:Int)
    fun place(items:List<DeskWidget>,columns:Int):List<Cell>{
        val occupied=mutableSetOf<Pair<Int,Int>>()
        return items.map{item->
            val width=if(item.kind=="Clock")1 else span(item,columns)
            val height=if(item.kind=="Clock"&&item.size==2)4 else when(item.size){0->1;1->2;else->3}
            var row=0;var column=0
            while(true){val free=column+width<=columns&&(row until row+height).all{y->(column until column+width).all{x->(x to y) !in occupied}};if(free)break;column++;if(column>=columns){column=0;row++}}
            for(y in row until row+height)for(x in column until column+width)occupied.add(x to y)
            Cell(item,column,row,width,height)
        }
    }
}
