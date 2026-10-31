import { useCallback, useReducer } from "react";
import type { EditorDocument } from "../domain/map";
import { applyOperation } from "../domain/operations";
import type { Operation } from "../domain/operations";
import { newDocument } from "../format/codec";
type State={present:EditorDocument;past:EditorDocument[];future:EditorDocument[];message?:string};
type Action={type:"op";op:Operation;assetKeys?:ReadonlySet<string>}|{type:"undo"}|{type:"redo"}|{type:"load";doc:EditorDocument}|{type:"setMapId";mapId:string};
function reducer(s:State,a:Action):State { try { if(a.type==="op"){const next=applyOperation(s.present,a.op,a.assetKeys);return {present:next,past:[...s.past,s.present],future:[],message:undefined}} if(a.type==="undo"){const p=s.past.at(-1);return p?{present:p,past:s.past.slice(0,-1),future:[s.present,...s.future]}:s} if(a.type==="redo"){const n=s.future[0];return n?{present:n,past:[...s.past,s.present],future:s.future.slice(1)}:s} if(a.type==="setMapId"){const update=(doc:EditorDocument):EditorDocument=>({...doc,map:{...doc.map,mapId:a.mapId}});return {...s,present:update(s.present),past:s.past.map(update),future:s.future.map(update)}} return {present:a.doc,past:[],future:[],message:undefined} } catch(e){return {...s,message:e instanceof Error?e.message:String(e)}}}
export function useEditorStore(assetKeys?:ReadonlySet<string>){
  const [state,dispatch]=useReducer(reducer,{present:newDocument(),past:[],future:[]});
  const operation=useCallback((op:Operation)=>dispatch({type:"op",op,assetKeys}),[assetKeys]);
  const undo=useCallback(()=>dispatch({type:"undo"}),[]);
  const redo=useCallback(()=>dispatch({type:"redo"}),[]);
  const load=useCallback((doc:EditorDocument)=>dispatch({type:"load",doc}),[]);
  const setMapId=useCallback((mapId:string)=>dispatch({type:"setMapId",mapId}),[]);
  return {state,operation,undo,redo,load,setMapId};
}
