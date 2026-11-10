"""Small, fail-closed patches for the pinned Antigravity/Leostryx web bundle."""
OLD_MODEL_ROWS = 'a.map(h=>\nA.createElement(RXb,{key:h.label,model:h,onEdit:c}))'
NEW_MODEL_ROWS = '''(window.__agyGroupProviderModels?window.__agyGroupProviderModels(a):[{id:"custom",name:"",models:a}]).map(group=>A.createElement("div",{key:group.id},group.name&&A.createElement("div",{className:"px-2 pt-2 pb-1 text-[11px] font-medium text-muted-foreground border-t border-border/50",role:"presentation"},group.name),group.models.map(h=>A.createElement(RXb,{key:h.label,model:h,onEdit:c}))))'''

def patch_bundle(source):
    if source.count(OLD_MODEL_ROWS) != 1:
        raise ValueError('Unexpected base web bundle; model selector patch does not match exactly once')
    return source.replace(OLD_MODEL_ROWS, NEW_MODEL_ROWS).replace('Antigravity Projects', 'Leostryx Projects').replace('Antigravity Backup', 'Leostryx Backup')
