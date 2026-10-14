import { useCallback, useReducer } from "react";
import { EditorDocument } from "../domain/map";
import { applyOperation, Operation } from "../domain/operations";
import { newDocument } from "../format/codec";
type State={present:EditorDocument;past:EditorDocument[];future:EditorDocument[];message?:string};
type Action={type:"op";op:Operation}|{type:"undo"}|{type:"redo"}|{type:"load";doc:EditorDocument};
function reducer(s:State,a:Action):State { try { if(a.type==="op"){const next=applyOperation(s.present,a.op);return {present:next,past:[...s.past,s.present],future:[],message:undefined}} if(a.type==="undo"){const p=s.past.at(-1);return p?{present:p,past:s.past.slice(0,-1),future:[s.present,...s.future]}:s} if(a.type==="redo"){const n=s.future[0];return n?{present:n,past:[...s.past,s.present],future:s.future.slice(1)}:s} return {present:a.doc,past:[],future:[],message:undefined} } catch(e){return {...s,message:e instanceof Error?e.message:String(e)}}}
export function useEditorStore(){const [state,dispatch]=useReducer(reducer,{present:newDocument(),past:[],future:[]});const operation=useCallback((op:Operation)=>dispatch({type:"op",op}),[]);return {state,operation,undo:()=>dispatch({type:"undo"}),redo:()=>dispatch({type:"redo"}),load:(doc:EditorDocument)=>dispatch({type:"load",doc})};}
