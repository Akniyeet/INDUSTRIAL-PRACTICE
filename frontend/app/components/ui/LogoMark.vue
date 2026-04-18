<script setup lang="ts">
/**
 * Animated Webizon logo mark.
 *
 * Props:
 *  - size: pixel size (default 40)
 *  - animate: enable entrance animation (default true)
 *  - pulse: enable continuous subtle pulse (default false)
 */
defineProps<{
  size?: number
  animate?: boolean
  pulse?: boolean
}>()
</script>

<template>
  <div
    class="logo-mark inline-flex items-center justify-center"
    :class="{ 'logo-animate': animate !== false, 'logo-pulse': pulse }"
    :style="{ width: `${size ?? 40}px`, height: `${size ?? 40}px` }"
  >
    <svg
      :width="size ?? 40"
      :height="size ?? 40"
      viewBox="0 0 406 406"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <!-- Ring (donut) -->
      <path
        class="logo-ring"
        d="M206.901 369.682C161.418 369.682 118.657 351.97 86.4953 319.808C54.3337 287.647 36.6211 244.886 36.6211 199.402C36.6211 153.919 54.3332 111.158 86.4953 78.9963C118.657 46.8347 161.418 29.1221 206.901 29.1221C252.385 29.1221 295.146 46.8342 327.307 78.9963C359.469 111.158 377.182 153.919 377.182 199.402C377.182 244.886 359.469 287.647 327.307 319.808C295.146 351.97 252.384 369.682 206.901 369.682ZM206.901 97.6455C150.792 97.6455 105.145 143.294 105.145 199.402C105.145 255.511 150.792 301.159 206.901 301.159C263.01 301.159 308.658 255.511 308.658 199.402C308.658 143.294 263.01 97.6455 206.901 97.6455Z"
        fill="url(#lm_g0)"
      />
      <!-- Spiral arc -->
      <path
        class="logo-spiral"
        d="M206.901 369.683C161.418 369.683 118.657 351.97 86.4949 319.808C65.8542 299.168 50.7987 273.473 42.9553 245.503C35.3643 218.431 34.5943 189.606 40.7295 162.146C44.8547 143.679 63.1683 132.054 81.6371 136.179C100.104 140.304 111.73 158.619 107.604 177.086C99.9517 211.34 110.174 246.58 134.948 271.354C154.168 290.574 179.721 301.159 206.901 301.159C234.082 301.159 259.635 290.574 278.855 271.354C298.074 252.135 308.658 226.582 308.658 199.402C308.658 172.221 298.074 146.668 278.855 127.449C253.943 102.537 218.529 92.3534 184.122 100.209C165.675 104.418 147.306 92.8798 143.094 74.4326C138.883 55.9849 150.423 37.6163 168.87 33.4042C196.436 27.1107 225.395 27.7841 252.619 35.3488C280.742 43.1634 306.569 58.2565 327.307 78.9953C359.469 111.157 377.181 153.918 377.181 199.401C377.181 244.885 359.469 287.646 327.307 319.807C295.146 351.97 252.385 369.683 206.901 369.683Z"
        fill="url(#lm_g1)"
      />
      <!-- Quarter arc -->
      <path
        class="logo-arc"
        d="M206.901 369.683C161.418 369.683 118.657 351.97 86.4947 319.808C73.115 306.428 73.115 284.735 86.4947 271.355C99.875 257.975 121.568 257.976 134.948 271.355C154.167 290.575 179.721 301.159 206.901 301.159C234.081 301.159 259.635 290.575 278.854 271.355C298.074 252.136 308.658 226.583 308.658 199.402C308.658 172.222 298.073 146.669 278.854 127.45C265.475 114.069 265.475 92.3765 278.854 78.9963C292.234 65.6171 313.928 65.6171 327.308 78.9963C359.469 111.158 377.181 153.919 377.181 199.402C377.181 244.886 359.469 287.647 327.308 319.808C295.146 351.97 252.384 369.683 206.901 369.683Z"
        fill="url(#lm_g2)"
      />
      <!-- Inner circle fill — prevents dark background showing through the donut hole -->
      <circle class="logo-center" cx="206.9" cy="199.4" r="100" fill="white" />
      <!-- Play button -->
      <path
        class="logo-play"
        d="M250.513 193.755L185.216 156.056C180.869 153.546 175.435 156.683 175.435 161.703V237.101C175.435 242.121 180.869 245.259 185.216 242.749L250.513 205.049C254.86 202.54 254.86 196.265 250.513 193.755Z"
        fill="url(#lm_g3)"
      />
      <!-- Outer orbit thin ring — wrapped in <g> for proper rotation -->
      <g class="logo-orbit" style="transform-origin: 203px 199px; transform-box: view-box">
        <path
          d="M206.598 379.619C158.46 379.619 113.204 360.873 79.1655 326.834C45.1277 292.796 26.3818 247.54 26.3818 199.403C26.3818 151.266 45.1277 106.009 79.1655 71.9713C113.204 37.9324 158.46 19.187 206.598 19.187C207.544 19.187 208.311 19.954 208.311 20.9003C208.311 21.8467 207.544 22.6132 206.598 22.6132C109.116 22.6132 29.808 101.921 29.808 199.403C29.808 296.885 109.116 376.192 206.598 376.192C304.08 376.192 383.387 296.885 383.387 199.403C383.387 198.456 384.154 197.689 385.101 197.689C386.047 197.689 386.814 198.456 386.814 199.403C386.814 247.541 368.068 292.796 334.03 326.834C299.991 360.873 254.735 379.619 206.598 379.619Z"
          fill="#215CFF"
        />
      </g>
      <!-- Outermost orbit — wrapped in <g> for proper rotation -->
      <g class="logo-orbit-outer" style="transform-origin: 203px 199px; transform-box: view-box">
        <path
          d="M206.597 398.805C205.651 398.805 204.884 398.038 204.884 397.092C204.884 396.145 205.651 395.379 206.597 395.379C314.659 395.379 402.573 307.464 402.573 199.403C402.573 91.3411 314.658 3.42613 206.597 3.42613C98.5359 3.42613 10.6205 91.3406 10.6205 199.402C10.6205 200.349 9.85352 201.115 8.90767 201.115C7.96182 201.115 7.19434 200.348 7.19434 199.402C7.19434 146.14 27.9358 96.0652 65.5982 58.4038C103.261 20.7414 153.335 0 206.597 0C259.859 0 309.933 20.7414 347.596 58.4038C385.258 96.0652 406 146.14 406 199.402C406 252.664 385.258 302.739 347.596 340.401C309.933 378.063 259.859 398.805 206.597 398.805Z"
          fill="#215CFF"
        />
      </g>
      <!-- Decorative dot bottom-left -->
      <circle class="logo-dot logo-dot-1" cx="10.62" cy="219.96" r="7.2" fill="#215CFF" />
      <!-- Decorative dot bottom -->
      <circle class="logo-dot logo-dot-2" cx="190.84" cy="395.38" r="7.2" fill="#215CFF" />

      <defs>
        <linearGradient id="lm_g0" x1="254.553" y1="253.932" x2="41.3304" y2="9.93528" gradientUnits="userSpaceOnUse">
          <stop stop-color="#215CFF"/><stop offset="1" stop-color="#FF428D"/>
        </linearGradient>
        <linearGradient id="lm_g1" x1="278.611" y1="346.263" x2="114.481" y2="9.94204" gradientUnits="userSpaceOnUse">
          <stop stop-color="#215CFF"/><stop offset="1" stop-color="#FF428D"/>
        </linearGradient>
        <linearGradient id="lm_g2" x1="407.578" y1="154.611" x2="-54.0366" y2="372.229" gradientUnits="userSpaceOnUse">
          <stop stop-color="#215CFF"/><stop offset="1" stop-color="#FF428D"/>
        </linearGradient>
        <linearGradient id="lm_g3" x1="285.292" y1="199.403" x2="131.632" y2="199.403" gradientUnits="userSpaceOnUse">
          <stop stop-color="#215CFF"/><stop offset="1" stop-color="#FF428D"/>
        </linearGradient>
      </defs>
    </svg>
  </div>
</template>

<style>
/*
 * IMPORTANT: NOT scoped — Vue scoped CSS cannot reach SVG child elements.
 * All class names are prefixed with `logo-` to avoid collisions.
 */
/* ------------------------------------------------------------------ */
/* Entrance animation: staggered reveal of each logo layer            */
/* ------------------------------------------------------------------ */
.logo-animate .logo-orbit-outer {
  opacity: 0;
  animation: logo-fade-spin 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0s forwards;
}
.logo-animate .logo-orbit {
  opacity: 0;
  animation: logo-fade-spin 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.1s forwards;
}
.logo-animate .logo-center {
  opacity: 0;
  transform-origin: center;
  animation: logo-scale-in 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.12s forwards;
}
.logo-animate .logo-ring {
  opacity: 0;
  transform-origin: center;
  animation: logo-scale-in 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.15s forwards;
}
.logo-animate .logo-spiral {
  opacity: 0;
  transform-origin: center;
  animation: logo-scale-in 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.25s forwards;
}
.logo-animate .logo-arc {
  opacity: 0;
  transform-origin: center;
  animation: logo-scale-in 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.35s forwards;
}
.logo-animate .logo-play {
  opacity: 0;
  transform-origin: 212px 199px;
  animation: logo-play-pop 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) 0.5s forwards;
}
.logo-animate .logo-dot-1 {
  opacity: 0;
  animation: logo-dot-pop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) 0.65s forwards;
}
.logo-animate .logo-dot-2 {
  opacity: 0;
  animation: logo-dot-pop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) 0.75s forwards;
}

/* ------------------------------------------------------------------ */
/* Continuous pulse (optional)                                         */
/* ------------------------------------------------------------------ */
.logo-pulse .logo-play {
  transform-origin: 203px 199px;
  animation: logo-play-glow 2s ease-in-out infinite;
}
.logo-pulse .logo-orbit {
  animation: logo-orbit-rotate 25s linear infinite;
}
.logo-pulse .logo-orbit-outer {
  animation: logo-orbit-rotate 35s linear infinite;
}
.logo-pulse .logo-dot-1 {
  animation: logo-dot-blink 3s ease-in-out infinite;
}
.logo-pulse .logo-dot-2 {
  animation: logo-dot-blink 3s ease-in-out 1.5s infinite;
}

/* ------------------------------------------------------------------ */
/* Keyframes                                                           */
/* ------------------------------------------------------------------ */
@keyframes logo-fade-spin {
  from { opacity: 0; transform: rotate(-30deg) scale(0.9); }
  to   { opacity: 1; transform: rotate(0deg) scale(1); }
}
@keyframes logo-scale-in {
  from { opacity: 0; transform: scale(0.7); }
  to   { opacity: 1; transform: scale(1); }
}
@keyframes logo-play-pop {
  0%   { opacity: 0; transform: scale(0) translateX(-5px); }
  70%  { opacity: 1; transform: scale(1.15) translateX(0); }
  100% { opacity: 1; transform: scale(1) translateX(0); }
}
@keyframes logo-dot-pop {
  0%   { opacity: 0; r: 0; }
  70%  { opacity: 1; r: 9; }
  100% { opacity: 1; r: 7.2; }
}
@keyframes logo-play-glow {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%      { opacity: 0.5; transform: scale(0.92); }
}
@keyframes logo-orbit-rotate {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
@keyframes logo-dot-blink {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.3; }
}
</style>
