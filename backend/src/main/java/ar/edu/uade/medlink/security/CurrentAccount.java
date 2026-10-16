package ar.edu.uade.medlink.security;

public record CurrentAccount(long id, String rol, String jti, long tokenVersion) { }
