<script setup>
defineProps({
  compact: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <div class="brand" :class="{ 'brand--compact': compact }" aria-label="MU AI">
    <span class="brand__mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none">
        <path d="M8 21V11l8 7 8-7v10" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
        <circle cx="16" cy="7" r="2" fill="currentColor" />
        <circle cx="6" cy="24.5" r="1.5" fill="currentColor" opacity=".55" />
        <circle cx="26" cy="24.5" r="1.5" fill="currentColor" opacity=".55" />
      </svg>
    </span>
    <span v-if="!compact" class="brand__text">MU AI</span>
  </div>
</template>
