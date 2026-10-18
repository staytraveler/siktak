package com.siktak.notification.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 템플릿. {@code {{변수}}}를 값으로 치환한다. 값이 없는 변수는 빈 문자열이 된다. */
@Getter
@Entity
@Table(name = "notification_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate {

    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

    @Id
    private String code;

    @Column(name = "title_template", nullable = false)
    private String titleTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Column(nullable = false)
    private boolean active;

    public String renderTitle(Map<String, String> vars) {
        return render(titleTemplate, vars);
    }

    public String renderBody(Map<String, String> vars) {
        return render(bodyTemplate, vars);
    }

    static String render(String template, Map<String, String> vars) {
        Matcher m = VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString();
    }
}
