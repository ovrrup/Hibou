"""Small, fail-closed patches for the pinned Antigravity/Hibou web bundle."""
OLD_MODEL_ROWS = 'a.map(h=>\nA.createElement(RXb,{key:h.label,model:h,onEdit:c}))'
NEW_MODEL_ROWS = '''(window.__agyGroupProviderModels?window.__agyGroupProviderModels(a):[{id:"custom",name:"",models:a}]).map(group=>A.createElement("div",{key:group.id},group.name&&A.createElement("div",{className:"px-2 pt-2 pb-1 text-[11px] font-medium text-muted-foreground border-t border-border/50",role:"presentation"},group.name),group.models.map(h=>A.createElement(RXb,{key:h.label,model:h,onEdit:c}))))'''

def patch_bundle(source):
    if source.count(OLD_MODEL_ROWS) != 1:
        raise ValueError('Unexpected base web bundle; model selector patch does not match exactly once')
    return (source.replace(OLD_MODEL_ROWS, NEW_MODEL_ROWS)
            .replace('Antigravity Projects', 'Hibou Projects')
            .replace('Antigravity Backup', 'Hibou Backup')
            .replace('Leostryx Projects', 'Hibou Projects')
            .replace('Leostryx Backup', 'Hibou Backup')
            .replace('Open in Antigravity IDE', 'Open in Hibou IDE')
            .replace('Antigravity Interactive HTML', 'Hibou Interactive HTML')
            .replace('running Antigravity', 'running Hibou')
            .replace("Antigravity's Browser", "Hibou's Browser")
            .replace('quit Antigravity', 'quit Hibou')
            .replace('Jetski Web', 'Hibou')
            .replace('/antigravityCascadeDone.mp3', '/hibouCascadeDone.mp3')
            .replace('/antigravityActionRequired.mp3', '/hibouActionRequired.mp3'))
