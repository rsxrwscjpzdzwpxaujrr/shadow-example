#version 110

uniform vec2 center;

void main() {
    vec2 a = center - gl_FragCoord.xy;
    gl_FragColor = gl_Color / (pow(dot(a, a), 0.4) / 32.0);
}