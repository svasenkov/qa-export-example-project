<html>
<#-- @ftlvariable name="data" type="io.qameta.allure.attachment.http.HttpResponseAttachment" -->
<head>
<meta charset="UTF-8">
<style>
*{box-sizing:border-box}
html,body{margin:0;padding:0;height:auto;overflow:visible}
body{font:13px/1.4 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;padding:4px 6px}
h4{margin:8px 0 4px;font-size:12px;opacity:.7}
pre{margin:0;white-space:pre-wrap;word-break:break-all;overflow:visible;background:transparent}
code{font-family:ui-monospace,monospace;font-size:13px;background:transparent;color:inherit}
pre code{display:block;padding:0;overflow:visible}
.hl-status,.hl-header-name{color:#0d9488;font-weight:600}
.hl-url,.hl-json-string,.hl-header-value{color:#db2777}
.hl-json-key{color:#0284c7}
</style>
</head>
<body style="margin:0;padding:4px 6px;overflow:visible;height:auto;background:transparent">
<div><h4 style="margin:8px 0 4px;font-size:12px;opacity:.7">Status code</h4><pre style="margin:0;overflow:visible;background:transparent"><code style="display:block;padding:0;background:transparent;color:inherit;font-family:ui-monospace,monospace;font-size:13px;overflow:visible"><span class="hl-status" style="color:#0d9488;font-weight:600"><#if data.responseCode??>${data.responseCode}<#else>Unknown</#if></span></code></pre></div>
<#if data.url??>
<div><pre style="margin:0;overflow:visible;background:transparent"><code style="display:block;padding:0;background:transparent;color:inherit;font-family:ui-monospace,monospace;font-size:13px;overflow:visible"><span class="hl-url" style="color:#db2777">${data.url}</span></code></pre></div>
</#if>
<#if (data.headers)?has_content>
<h4 style="margin:8px 0 4px;font-size:12px;opacity:.7">Headers</h4>
<div><pre style="margin:0;overflow:visible;background:transparent"><code style="display:block;padding:0;background:transparent;color:inherit;font-family:ui-monospace,monospace;font-size:13px;white-space:pre-wrap;overflow:visible"><#list data.headers as name, value><span class="hl-header-name" style="color:#0d9488;font-weight:600">${name}</span>: <span class="hl-header-value" style="color:#db2777">${value}</span>
</#list></code></pre></div>
</#if>
<#if data.body??>
<h4 style="margin:8px 0 4px;font-size:12px;opacity:.7">Body</h4>
<div><pre style="margin:0;overflow:visible;background:transparent"><code class="hl-json" style="display:block;padding:0;background:transparent;color:inherit;font-family:ui-monospace,monospace;font-size:13px;white-space:pre-wrap;overflow:visible">${data.body}</code></pre></div>
</#if>
<#if (data.cookies)?has_content>
<h4 style="margin:8px 0 4px;font-size:12px;opacity:.7">Cookies</h4>
<div><pre style="margin:0;overflow:visible;background:transparent"><code style="display:block;padding:0;background:transparent;color:inherit;font-family:ui-monospace,monospace;font-size:13px;white-space:pre-wrap;overflow:visible"><#list data.cookies as name, value><span class="hl-header-name" style="color:#0d9488;font-weight:600">${name}</span>: <span class="hl-header-value" style="color:#db2777">${value}</span>
</#list></code></pre></div>
</#if>
<script>
(function(){
var e=function(s){return(s+'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')};
function json(el){
  var t=el.textContent,i=0,h='',s,j;
  for(;i<t.length;i++)
    if(t[i]==='"'){
      s=i++;
      while(i<t.length){if(t[i]==='\\')i+=2;else if(t[i]==='"'){i++;break;}else i++}
      j=i;while(j<t.length&&/\s/.test(t[j]))j++;
      h+='<span class="hl-'+(t[j]===':'?'json-key':'json-string')+'" style="color:'+(t[j]===':'?'#0284c7':'#db2777')+'">'+e(t.slice(s,i))+'</span>';
      i--;
    }else h+=e(t[i]);
  el.innerHTML=h;
}
var codes=document.body.querySelectorAll('code.hl-json');
for(var i=0;i<codes.length;i++) codes[i].textContent.trim().match(/^[{[]/)&&json(codes[i]);
})();
</script>
</body>
</html>
