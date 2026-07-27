package pt.seerhub.community.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import pt.seerhub.community.domain.CommunityPermission;

/**
 * Declara, num handler MVC de {@code /api/communities/{slug}/...}, a
 * {@link CommunityPermission} que esse endpoint exige (R4, contrato C2 do
 * plano de F04). Aplicada pelo {@link CommunityPermissionInterceptor} — não
 * é decorativa: sem ela (ou sem constar da lista justificada de exceções em
 * {@code CommunityAuthorizationContractIT}), o endpoint fica sem porta de
 * permissão de comunidade.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresCommunityPermission {

    CommunityPermission value();

    /** Nome da variável de caminho que contém o slug da comunidade. */
    String slugVariable() default "slug";
}
