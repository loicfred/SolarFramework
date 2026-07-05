package org.solarframework.proxyserver.web.dbeditor.spring.v1;

import jakarta.persistence.Column;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.web.spring.WebUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.*;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Controller
@RequestMapping("/admin/db/v1")
public class DatabaseEditorController {

    @GetMapping("/{item}/edit")
    public String adminEdit(Model model, Principal loggedUser, @PathVariable String item) {
        return adminEdit(model, loggedUser, item, null);
    }
    @GetMapping("/{item}/edit/{id}")
    public String adminEdit(Model model, Principal loggedUser, @PathVariable String item, @PathVariable Long id) {
        if (loggedUser == null) return "redirect:/auth/v1/login";
        if (!WebUtils.canAccessPage(model, loggedUser, "/admin/db/**")) return "redirect:/";
        try {
            item = item.substring(0,1).toUpperCase() + item.substring(1);
            List<FieldMeta> fields = new ArrayList<>();
            Class<?> objClass = SolarDBManager.getServiceByEntity(item).getClassOfTable(item);
            Object entity = id != null ? SolarDBManager.getById(objClass, id).orElseThrow() : null;
            for (Field field : objClass.getDeclaredFields()) {
                field.setAccessible(true);
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isTransient(field.getModifiers())) continue;
                if (field.getType().equals(Instant.class)) continue;

                FieldMeta meta = new FieldMeta();
                if (field.getAnnotation(Column.class) != null) meta.colName = field.getAnnotation(Column.class).name();
                meta.name = field.getName();
                meta.type = field.getType().getSimpleName();
                if (entity != null) meta.value = field.get(entity);
                fields.add(meta);
            }
            UniversalForm form = new UniversalForm();
            form.fields = fields;
            model.addAttribute("form", form);

            model.addAttribute("item", item);
            model.addAttribute("id", id);
        } catch (Exception ignored) {
            return "redirect:/admin?page=3&errorDb";
        }
        return "dbeditor/v1/editor";
    }

    @PostMapping("/{objectName}/update")
    public String updateItem(Model model, Principal loggedUser, RedirectAttributes redirectAttributes, @PathVariable String objectName, @ModelAttribute UniversalForm form) {
        return updateItem(model, loggedUser, redirectAttributes, objectName, null, form);
    }
    @PostMapping("/{objectName}/update/{id}")
    public String updateItem(Model model, Principal loggedUser, RedirectAttributes redirectAttributes, @PathVariable String objectName, @PathVariable Object id, @ModelAttribute UniversalForm form) {
        if (loggedUser == null) return "redirect:/auth/v1/login";
        if (!WebUtils.canAccessPage(model, loggedUser, "/admin/db/**")) return "redirect:/";
        try {
            objectName = objectName.substring(0,1).toUpperCase() + objectName.substring(1);
            @SuppressWarnings("unchecked")
            Class<? extends DatabaseObject.ID_OBJ<?, ?>> objClass = (Class<? extends DatabaseObject.ID_OBJ<?, ?>>) SolarDBManager.getServiceByEntity(objectName).getClassOfTable(objectName).asSubclass(DatabaseObject.class);
            DatabaseObject.ID_OBJ<?,?> entity = id != null ? SolarDBManager.getById(objClass, id).orElseThrow() : null;
            if (entity == null) {
                Constructor<DatabaseObject.ID_OBJ<?,?>> ctor = (Constructor<DatabaseObject.ID_OBJ<?,?>>) objClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                entity = ctor.newInstance();
            }

            for (FieldMeta entry : form.fields) {
                Field field = entity.getClass().getDeclaredField(entry.name);
                field.setAccessible(true);
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isTransient(field.getModifiers())) continue;
                if (field.getName().endsWith("CreatedAt")) continue;
                if (field.getName().endsWith("UpdatedAt")) continue;
                if (field.getName().endsWith("Password")) continue;
                if (field.getName().endsWith("ID")) continue;
                Convert(field, entity, entry.value);
            }
            try {
                Field updatedAt = objClass.getField("UpdatedAt");
                updatedAt.setAccessible(true);
                updatedAt.set(entity, LocalDateTime.now());
            } catch (Exception ignored) {}

            if (id == null) {
                entity = (DatabaseObject.ID_OBJ<?,?>) entity.WriteThenReturn().orElse(null);
                id = entity.getID();
            } else {
                entity.Update();
            }
            List<String> img = form.getFields().stream().filter(f -> f.getType().equals(byte[].class.getSimpleName())).map(FieldMeta::getName).toList();
            if (!img.isEmpty()) {
                entity.UpdateOnly(img.toArray(String[]::new));
                SolarDBManager.resetCache("IMG");
            }
            redirectAttributes.addFlashAttribute("success", "Entry updated successfully.");
            return "redirect:/admin/db/v1/edit/" + objectName + (id != null ? "/" + id : "");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred: " + e.getMessage());
            return "redirect:/admin/db/v1/edit/" + objectName + (id != null ? "/" + id : "");
        }
    }

    @ResponseBody
    @GetMapping("/list/{item}")
    public Map<String, ?> fetchItemList(Principal loggedUser, @PathVariable String item) {
        if (loggedUser == null) return null;
        try {
            Class<?> Account_User = Class.forName("org.solarframework.web.auth.obj.Account_User");
            Object result = Account_User.getDeclaredMethod("getByAuthentication", Principal.class).invoke(null, loggedUser);
            String role = (String) result.getClass().getMethod("getRole").invoke(result);
            if (!"ADMIN".equalsIgnoreCase(role)) return null;
        } catch (Exception ignored) {}
        try {
            item = item.substring(0, 1).toUpperCase() + item.substring(1);
            Class<?> objClass = SolarDBManager.getServiceByEntity(item).getClassOfTable(item);
            return Map.of("tblstats", SolarDBManager.getServiceByEntity(objClass).getTableStats(item), "items", SolarDBManager.getAll(objClass));
        } catch (Exception e) {
            return null;
        }
    }
    public void Convert(Field field, Object entity, Object value) {
        try {
            Type type = field.getType();
            if (type.equals(String.class)) {
                field.set(entity, value.toString());
            } else if (type.equals(Integer.class) || type.equals(int.class)) {
                field.set(entity, Integer.parseInt(value.toString()));
            } else if (type.equals(Long.class) || type.equals(long.class)) {
                field.set(entity, Long.parseLong(value.toString()));
            } else if (type.equals(Double.class) || type.equals(double.class)) {
                field.set(entity, Double.parseDouble(value.toString()));
            } else if (type.equals(Float.class) || type.equals(float.class)) {
                field.set(entity, Float.parseFloat(value.toString()));
            } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
                if (value instanceof String) {
                    field.set(entity, Boolean.parseBoolean((String)value));
                } else {
                    field.set(entity, value);
                }
            } else if (type.equals(LocalDate.class)) {
                field.set(entity, LocalDate.parse(value.toString())); // optionally use formatter
            } else if (type.equals(LocalDateTime.class)) {
                if (value instanceof String s && !s.isBlank()) {
                    field.set(entity, LocalDateTime.parse(value.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
                }
            } else if (type.equals(byte[].class)) {
                if (value instanceof MultipartFile F && !F.isEmpty()) {
                    field.set(entity, F.getBytes());
                }
            } else {
                field.set(entity, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class UniversalForm {
        public List<FieldMeta> fields;

        public List<FieldMeta> getFields() {
            return fields;
        }
        public void setFields(List<FieldMeta> fields) {
            this.fields = fields;
        }
    }

    public static class FieldMeta {
        public String colName;
        public String name;
        public String type;
        public Object value = null;
        public Boolean booleanValue = null;

        public String getColName() {
            return colName;
        }
        public void setColName(String colName) {
            this.colName = colName;
        }

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }

        public Object getValue() {
            if (value instanceof LocalDateTime L) value = L.truncatedTo(ChronoUnit.MINUTES);
            return value;
        }
        public void setValue(Object value) {
            this.value = value;
        }


        public Boolean getBooleanValue() {
            return (value instanceof Boolean) ? (Boolean) value : false;
        }
        public void setBooleanValue(Boolean value) {
            this.value = value;
        }
    }
}
