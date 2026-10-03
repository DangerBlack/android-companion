# Build from the repository root:
#   docker build -f docker/relay/Dockerfile -t <user>/android-companion-relay:latest .

FROM binwiederhier/ntfy:v2.28.0
ENV NTFY_AUTH_FILE=/var/lib/ntfy/user.db
COPY docker/relay/entrypoint.sh /usr/local/bin/companion-entrypoint
USER root
RUN chmod +x /usr/local/bin/companion-entrypoint
ENTRYPOINT ["/usr/local/bin/companion-entrypoint"]
