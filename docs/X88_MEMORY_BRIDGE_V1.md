# X88 Memory Bridge V1

Der X88 Memory Core erhält einen provider-neutralen Importkanal. Meta AI ist dabei nur eine mögliche Quelle, nicht die Grundlage des Memory-Modells.

## Speicherstruktur
Jeder Memory-Eintrag behält Quelle, Zeitpunkt, Projekt/Thema, Bucket, Inhaltstyp, optionale Quellenreferenz und eine SHA-256-Inhaltskennung.

## Verarbeitung
Quelle -> X88MemoryBridge -> Normalisierung -> SHA-256-Hash -> Deduplizierung -> X88MemoryItem

Die Normalisierung entfernt führende/abschließende Leerzeichen und vereinheitlicht zusammenhängende Whitespace-Sequenzen. Doppelte Inhalte werden anhand ihres normalisierten SHA-256-Inhaltshashes verworfen.

## Sicherheitsgrenze
V1 verarbeitet nur Daten, die über einen autorisierten Export, Import oder eine andere offiziell erlaubte Schnittstelle bereitgestellt werden. Keine privaten Meta-Schnittstellen und kein Umgehen von Plattformschutz.

## Architekturziel
Die Quelle kann später ausgetauscht werden, ohne den X88 Memory Core umzubauen. Die Bridge ist bewusst noch keine Persistenzschicht: Persistenz bleibt eine getrennte Verantwortung und kann in einem nächsten Schritt an die vorhandene lokale Speicherarchitektur angeschlossen werden.
