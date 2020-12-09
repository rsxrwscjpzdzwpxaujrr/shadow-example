#version 110

uniform vec2 pos;

void main() {
    vec2 a = pos - gl_FragCoord.xy;
    gl_FragColor = gl_Color / (pow(dot(a, a), 0.4) / 32.0);
}
