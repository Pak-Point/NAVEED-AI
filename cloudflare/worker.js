const SYSTEM_PROMPT = `You are NAVEED AI, a friendly and careful personal assistant. The user may write in Urdu, Roman Urdu, Saraiki, or English. Reply in the user's language unless they select another language. Keep answers clear, useful, and respectful. Do not claim you can control the phone or access the internet unless a tool actually provides that ability. If unsure, say so. Never ask the user to reveal passwords, API keys, or private credentials.`;
const MODELS = ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash"];
const ALLOWED_ORIGIN = "https://pak-point.github.io";

export default {
 async fetch(request, env) {
  const origin = request.headers.get("Origin") || "";
  const cors = {
   "Access-Control-Allow-Origin": origin === ALLOWED_ORIGIN ? ALLOWED_ORIGIN : "null",
   "Access-Control-Allow-Methods": "POST, OPTIONS",
   "Access-Control-Allow-Headers": "Content-Type",
   "Vary": "Origin"
  };
  if (request.method === "OPTIONS") return new Response(null,{status:204,headers:cors});
  if (request.method !== "POST") return json({error:"Method not allowed"},405,cors);
  if (origin !== ALLOWED_ORIGIN) return json({error:"Origin not allowed"},403,cors);
  try {
   if (!env.GEMINI_API_KEY) return json({error:"Worker secret GEMINI_API_KEY is missing."},500,cors);
   const body=await request.json();
   const message=typeof body.message==="string"?body.message.trim():"";
   if(!message||message.length>8000)return json({error:"Message is empty or too long (maximum 8000 characters)."},400,cors);
   const language=typeof body.language==="string"?body.language:"same";
   const history=Array.isArray(body.history)?body.history.slice(-10):[];
   const transcript=history.map(item=>{const role=item&&item.role==="assistant"?"NAVEED AI":"User";const value=typeof item?.text==="string"?item.text.slice(0,4000):"";return value?role+": "+value:"";}).filter(Boolean).join("\n");
   const input="Preferred reply language: "+language+"\nConversation so far:\n"+transcript+"\nUser: "+message;
   let lastError="All available Gemini models are temporarily unavailable.";
   const deadline=Date.now()+25000;
   for(const model of MODELS){
    const remaining=deadline-Date.now();if(remaining<=500)break;
    let upstream;
    try{upstream=await fetch("https://generativelanguage.googleapis.com/v1beta/interactions",{method:"POST",headers:{"Content-Type":"application/json","x-goog-api-key":env.GEMINI_API_KEY},body:JSON.stringify({model,input,system_instruction:SYSTEM_PROMPT}),signal:AbortSignal.timeout(Math.min(9000,remaining))});}
    catch(error){lastError=error?.name==="TimeoutError"||error?.name==="AbortError"?"Gemini request timed out.":"Could not connect to Gemini: "+(error?.message||"network error");continue;}
    const raw=await upstream.text();let result;try{result=raw?JSON.parse(raw):{};}catch{return json({error:"Gemini returned non-JSON response (HTTP "+upstream.status+")."},502,cors);}
    if(!upstream.ok){lastError=result?.error?.message||result?.message||("Gemini API returned HTTP "+upstream.status);if([429,502,503].includes(upstream.status))continue;return json({error:lastError},upstream.status,cors);}
    const reply=extractText(result);if(!reply)return json({error:"Gemini returned no text. Please try again."},502,cors);
    return json({reply,model},200,cors);
   }
   return json({error:"Gemini busy or timed out. Please try again. "+lastError},504,cors);
  }catch(error){return json({error:"Worker error: "+(error?.message||"Unknown error")},500,cors);}
 }
};
function extractText(result){
 if(typeof result?.output_text==="string"&&result.output_text.trim())return result.output_text.trim();
 const collections=[result?.steps,result?.outputs,result?.output].filter(Array.isArray);
 const parts=collections.flat().flatMap(item=>typeof item?.text==="string"?[{text:item.text}]:Array.isArray(item?.content)?item.content:[]);
 return parts.filter(part=>typeof part?.text==="string").map(part=>part.text).join("\n").trim();
}
function json(data,status,headers){return new Response(JSON.stringify(data),{status,headers:{...headers,"Content-Type":"application/json"}});}
