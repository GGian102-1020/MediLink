package ar.edu.uade.medilink.android.data

data class RecuperarClaveRequest(val correo: String)
data class RestablecerClaveRequest(val correo: String, val codigo: String, val nuevaContrasena: String)
